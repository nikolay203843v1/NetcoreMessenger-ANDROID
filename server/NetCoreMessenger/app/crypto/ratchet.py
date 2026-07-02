import os
from dataclasses import dataclass, field
from typing import Optional

from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


@dataclass
class RatchetState:
    root_key: bytes = b""
    send_chain_key: bytes = b""
    recv_chain_key: bytes = b""
    send_count: int = 0
    recv_count: int = 0
    our_dh_private: bytes = b""
    our_dh_public: bytes = b""
    their_dh_public: Optional[bytes] = None
    prev_their_dh_public: Optional[bytes] = None


def kdf_chain(ck: bytes, message_count: int) -> tuple[bytes, bytes, bytes]:
    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=76,
        salt=None,
        info=b"NetCoreRatchet",
    )
    output = hkdf.derive(ck + message_count.to_bytes(4, "big"))
    return output[:32], output[32:44], output[44:]


def kdf_root(rk: bytes, dh_output: bytes) -> tuple[bytes, bytes]:
    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=64,
        salt=None,
        info=b"NetCoreRoot",
    )
    output = hkdf.derive(rk + dh_output)
    return output[:32], output[32:]


def dh(private_key: bytes, public_key: bytes) -> bytes:
    from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey

    priv = X25519PrivateKey.from_private_bytes(private_key)
    pub = X25519PublicKey.from_public_bytes(public_key)
    return priv.exchange(pub)


def generate_dh_keypair() -> tuple[bytes, bytes]:
    from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey

    priv = X25519PrivateKey.generate()
    private_bytes = priv.private_bytes_raw()
    public_bytes = priv.public_key().public_bytes_raw()
    return private_bytes, public_bytes


def ratchet_init_shared(our_private: bytes, their_public: bytes, root_key: bytes) -> RatchetState:
    dh_out = dh(our_private, their_public)
    new_root, recv_chain = kdf_root(root_key, dh_out)
    return RatchetState(
        root_key=new_root,
        recv_chain_key=recv_chain,
        our_dh_private=our_private,
        our_dh_public=bytes(),
        their_dh_public=their_public,
    )


def ratchet_encrypt(state: RatchetState, plaintext: bytes, associated_data: bytes = b"") -> tuple[bytes, bytes, int]:
    if not state.send_chain_key:
        if not state.our_dh_private:
            priv, pub = generate_dh_keypair()
            state.our_dh_private = priv
            state.our_dh_public = pub
        dh_out = dh(state.our_dh_private, state.their_dh_public)
        state.root_key, state.send_chain_key = kdf_root(state.root_key, dh_out)

    state.send_chain_key, nonce, key = kdf_chain(state.send_chain_key, state.send_count)

    aes = AESGCM(key)
    ad = associated_data + state.send_count.to_bytes(4, "big")
    ciphertext = aes.encrypt(nonce, plaintext, ad)

    msg_n = state.send_count
    state.send_count += 1
    return ciphertext, state.our_dh_public, msg_n


def ratchet_decrypt(state: RatchetState, ciphertext: bytes, dh_public: bytes, message_n: int, associated_data: bytes = b"") -> Optional[bytes]:
    if state.their_dh_public != dh_public:
        state.prev_their_dh_public = state.their_dh_public
        state.their_dh_public = dh_public
        dh_out = dh(state.our_dh_private, dh_public)
        state.root_key, state.recv_chain_key = kdf_root(state.root_key, dh_out)
        priv, pub = generate_dh_keypair()
        state.our_dh_private = priv
        state.our_dh_public = pub

    next_ck, nonce, key = kdf_chain(state.recv_chain_key, message_n)
    state.recv_chain_key = next_ck

    aes = AESGCM(key)
    ad = associated_data + message_n.to_bytes(4, "big")
    try:
        return aes.decrypt(nonce, ciphertext, ad)
    except Exception:
        return None
