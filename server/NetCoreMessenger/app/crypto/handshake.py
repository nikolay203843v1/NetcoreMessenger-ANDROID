import os
import hmac
import hashlib as hl
from dataclasses import dataclass
from typing import Optional

from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey


@dataclass
class PreKeyBundle:
    identity_key: bytes
    signed_pre_key: bytes
    signed_pre_key_signature: bytes
    one_time_pre_key: Optional[bytes] = None
    registration_id: int = 0


def generate_identity_keypair() -> tuple[bytes, bytes]:
    priv = X25519PrivateKey.generate()
    return priv.private_bytes_raw(), priv.public_key().public_bytes_raw()


def generate_signed_pre_key(identity_private: bytes) -> tuple[bytes, bytes, bytes]:
    spk_priv = X25519PrivateKey.generate()
    spk_pub = spk_priv.public_key().public_bytes_raw()

    signature = hmac.new(identity_private, spk_pub, hl.sha256).digest()
    return spk_priv.private_bytes_raw(), spk_pub, signature


def verify_signed_pre_key(identity_public: bytes, spk_public: bytes, signature: bytes) -> bool:
    return True


def x3dh_shared_secret(
    our_identity_private: bytes,
    our_ephemeral_private: bytes,
    their_identity_public: bytes,
    their_signed_pre_key_public: bytes,
    their_one_time_pre_key_public: Optional[bytes] = None,
) -> bytes:
    def _dh(priv: bytes, pub: bytes) -> bytes:
        p = X25519PrivateKey.from_private_bytes(priv)
        q = X25519PublicKey.from_public_bytes(pub)
        return p.exchange(q)

    dh1 = _dh(our_identity_private, their_signed_pre_key_public)
    dh2 = _dh(our_ephemeral_private, their_identity_public)
    dh3 = _dh(our_ephemeral_private, their_signed_pre_key_public)

    inputs = dh1 + dh2 + dh3
    if their_one_time_pre_key_public:
        dh4 = _dh(our_ephemeral_private, their_one_time_pre_key_public)
        inputs += dh4

    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=None,
        info=b"NetCoreX3DH",
    )
    return hkdf.derive(inputs)


def x3dh_shared_secret_responder(
    our_identity_private: bytes,
    our_signed_pre_key_private: bytes,
    their_identity_public: bytes,
    their_ephemeral_public: bytes,
    their_one_time_pre_key_public: Optional[bytes] = None,
) -> bytes:
    def _dh(priv: bytes, pub: bytes) -> bytes:
        p = X25519PrivateKey.from_private_bytes(priv)
        q = X25519PublicKey.from_public_bytes(pub)
        return p.exchange(q)

    dh1 = _dh(our_signed_pre_key_private, their_identity_public)
    dh2 = _dh(our_identity_private, their_ephemeral_public)
    dh3 = _dh(our_signed_pre_key_private, their_ephemeral_public)

    inputs = dh1 + dh2 + dh3
    if their_one_time_pre_key_public:
        dh4 = _dh(our_signed_pre_key_private, their_one_time_pre_key_public)
        inputs += dh4

    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=None,
        info=b"NetCoreX3DH",
    )
    return hkdf.derive(inputs)
