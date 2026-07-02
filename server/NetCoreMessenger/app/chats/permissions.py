from app.chats.models import ChatParticipant


ROLE_HIERARCHY = {"member": 0, "admin": 1, "creator": 2}


def is_admin(participant: ChatParticipant) -> bool:
    return ROLE_HIERARCHY.get(participant.role, 0) >= ROLE_HIERARCHY["admin"]


def is_creator(participant: ChatParticipant) -> bool:
    return participant.role == "creator"


def can_remove_participant(actor: ChatParticipant, target: ChatParticipant) -> bool:
    return ROLE_HIERARCHY.get(actor.role, 0) > ROLE_HIERARCHY.get(target.role, 0)


def can_promote(actor: ChatParticipant) -> bool:
    return is_admin(actor)
