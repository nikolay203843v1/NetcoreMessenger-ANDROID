import json
import os
import secrets
from typing import Optional
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.crypto.models import IdentityKey, PreKey, Session
from app.crypto.handshake import (
    generate_identity_keypair,
    generate_signed_pre_key,
    verify_signed_pre_key,
    x3dh_shared_secret,
    x3dh_shared_secret_responder,
    PreKeyBundle,
)
from app.crypto.ratchet import (
    RatchetState,
    ratchet_init_shared,
    ratchet_encrypt,
    ratchet_decrypt,
    kdf_root,
    generate_dh_keypair,
    dh,
)

SESSION_CIPHERTEXT_TAG = b"NC_CRYPTO_V1"


class CryptoService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def create_identity(self, user_id: int) -> tuple[bytes, bytes]:
        priv, pub = generate_identity_keypair()
        key = IdentityKey(
            user_id=user_id,
            identity_public=pub,
            identity_private_encrypted=priv,
        )
        self.db.add(key)
        await self.db.flush()
        return priv, pub

    async def get_identity(self, user_id: int) -> Optional[IdentityKey]:
        result = await self.db.execute(
            select(IdentityKey).where(IdentityKey.user_id == user_id)
        )
        return result.scalar_one_or_none()

    async def generate_pre_keys(self, user_id: int, count: int = 10) -> list[dict]:
        identity = await self.get_identity(user_id)
        if not identity:
            raise ValueError("Identity not found")

        keys = []
        for _ in range(count):
            spk_priv, spk_pub, signature = generate_signed_pre_key(
                identity.identity_private_encrypted
            )
            pre_key = PreKey(
                user_id=user_id,
                key_id=secrets.randbelow(2**31),
                public_key=spk_pub,
                private_key_encrypted=spk_priv,
            )
            self.db.add(pre_key)
            keys.append({
                "key_id": pre_key.key_id,
                "public_key": spk_pub.hex(),
                "signature": signature.hex(),
            })
        await self.db.flush()
        return keys

    async def get_pre_key_bundle(self, user_id: int) -> Optional[PreKeyBundle]:
        identity = await self.get_identity(user_id)
        if not identity:
            return None

        result = await self.db.execute(
            select(PreKey)
            .where(PreKey.user_id == user_id, PreKey.is_used == False)
            .order_by(PreKey.id.asc())
            .limit(1)
        )
        spk = result.scalar_one_or_none()

        bundle = PreKeyBundle(
            identity_key=identity.identity_public,
            signed_pre_key=spk.public_key if spk else b"",
            signed_pre_key_signature=b"",
        )

        return bundle

    async def init_session(self, user_id: int, contact_id: int, bundle: PreKeyBundle) -> Session:
        identity = await self.get_identity(user_id)
        if not identity:
            raise ValueError("Identity not found for initiator")

        ephemeral_priv, ephemeral_pub = generate_dh_keypair()

        shared = x3dh_shared_secret(
            our_identity_private=identity.identity_private_encrypted,
            our_ephemeral_private=ephemeral_priv,
            their_identity_public=bundle.identity_key,
            their_signed_pre_key_public=bundle.signed_pre_key,
            their_one_time_pre_key_public=bundle.one_time_pre_key,
        )

        state = RatchetState(root_key=shared)
        state.our_dh_private = ephemeral_priv
        state.our_dh_public = ephemeral_pub
        state.their_dh_public = bundle.signed_pre_key

        serialized = self._serialize_state(state)
        sess = Session(
            user_id=user_id,
            contact_id=contact_id,
            session_data_encrypted=serialized,
        )
        self.db.add(sess)
        await self.db.flush()
        return sess

    async def encrypt_message(self, user_id: int, contact_id: int, plaintext: str) -> Optional[bytes]:
        result = await self.db.execute(
            select(Session).where(
                Session.user_id == user_id,
                Session.contact_id == contact_id,
            )
        )
        sess = result.scalar_one_or_none()
        if not sess:
            return None

        state = self._deserialize_state(sess.session_data_encrypted)

        identity = await self.get_identity(user_id)
        if not identity:
            return None

        pt = plaintext.encode("utf-8")
        ciphertext, dh_pub, msg_n = ratchet_encrypt(state, pt)

        sess.session_data_encrypted = self._serialize_state(state)
        await self.db.flush()

        payload = {
            "ciphertext": ciphertext.hex(),
            "dh_public": dh_pub.hex(),
            "message_n": msg_n,
            "sender_identity_key": identity.identity_public.hex(),
        }
        return json.dumps(payload).encode("utf-8")

    async def decrypt_message(self, user_id: int, contact_id: int, payload_bytes: bytes) -> Optional[str]:
        payload = json.loads(payload_bytes.decode("utf-8"))
        ciphertext = bytes.fromhex(payload["ciphertext"])
        dh_public = bytes.fromhex(payload["dh_public"])
        msg_n = payload["message_n"]

        result = await self.db.execute(
            select(Session).where(
                Session.user_id == user_id,
                Session.contact_id == contact_id,
            )
        )
        sess = result.scalar_one_or_none()

        if sess:
            state = self._deserialize_state(sess.session_data_encrypted)
            pt = ratchet_decrypt(state, ciphertext, dh_public, msg_n)
            if pt is None:
                return None
            sess.session_data_encrypted = self._serialize_state(state)
            await self.db.flush()
            return pt.decode("utf-8")

        identity = await self.get_identity(user_id)
        if not identity:
            return None

        spk_result = await self.db.execute(
            select(PreKey)
            .where(PreKey.user_id == user_id, PreKey.is_used == False)
            .order_by(PreKey.id.asc())
            .limit(1)
        )
        spk = spk_result.scalar_one_or_none()
        if not spk:
            return None

        sender_identity_key = bytes.fromhex(payload["sender_identity_key"])

        shared = x3dh_shared_secret_responder(
            our_identity_private=identity.identity_private_encrypted,
            our_signed_pre_key_private=spk.private_key_encrypted,
            their_identity_public=sender_identity_key,
            their_ephemeral_public=dh_public,
        )

        state = RatchetState(root_key=shared)
        state.our_dh_private = spk.private_key_encrypted
        state.our_dh_public = spk.public_key

        pt = ratchet_decrypt(state, ciphertext, dh_public, msg_n)
        if pt is None:
            return None

        spk.is_used = True

        new_sess = Session(
            user_id=user_id,
            contact_id=contact_id,
            session_data_encrypted=self._serialize_state(state),
        )
        self.db.add(new_sess)
        await self.db.flush()

        return pt.decode("utf-8")

    def _serialize_state(self, state: RatchetState) -> bytes:
        data = {
            "rk": state.root_key.hex(),
            "sck": state.send_chain_key.hex(),
            "rck": state.recv_chain_key.hex(),
            "sc": state.send_count,
            "rc": state.recv_count,
            "odp": state.our_dh_private.hex(),
            "odpub": state.our_dh_public.hex(),
            "tdp": state.their_dh_public.hex() if state.their_dh_public else "",
            "ptdp": state.prev_their_dh_public.hex() if state.prev_their_dh_public else "",
        }
        return SESSION_CIPHERTEXT_TAG + json.dumps(data).encode("utf-8")

    def _deserialize_state(self, raw: bytes) -> RatchetState:
        if raw.startswith(SESSION_CIPHERTEXT_TAG):
            raw = raw[len(SESSION_CIPHERTEXT_TAG):]
        data = json.loads(raw.decode("utf-8"))
        return RatchetState(
            root_key=bytes.fromhex(data["rk"]),
            send_chain_key=bytes.fromhex(data["sck"]),
            recv_chain_key=bytes.fromhex(data["rck"]),
            send_count=data["sc"],
            recv_count=data["rc"],
            our_dh_private=bytes.fromhex(data["odp"]),
            our_dh_public=bytes.fromhex(data["odpub"]),
            their_dh_public=bytes.fromhex(data["tdp"]) if data["tdp"] else None,
            prev_their_dh_public=bytes.fromhex(data["ptdp"]) if data["ptdp"] else None,
        )
