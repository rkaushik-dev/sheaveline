"""the towers get shot every autumn and the shots have to live somewhere

Revision ID: 0004
Revises: 0003
Created: 2026-09-05 10:19:19.868548
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import sheaveline.persistence.types  # noqa: F401  the columns are named in full

revision: str = "0004"
down_revision: str | None = "0003"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "alignment_surveys",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column(
            "lift_code", sheaveline.persistence.types.LiftCodeColumn(length=8), nullable=False
        ),
        sa.Column("taken", sa.Date(), nullable=False),
        sa.Column("surveyor", sa.String(length=64), nullable=False),
        sa.ForeignKeyConstraint(
            ["lift_code"], ["lifts.code"], name=op.f("fk_alignment_surveys_lift_code_lifts")
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_alignment_surveys")),
        sa.UniqueConstraint("lift_code", "taken", name="one_alignment_survey_a_day"),
    )
    with op.batch_alter_table("alignment_surveys", schema=None) as batch_op:
        batch_op.create_index("ix_alignment_by_machine", ["lift_code", "taken"], unique=False)

    op.create_table(
        "tower_shots",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("survey_id", sa.Integer(), nullable=False),
        sa.Column(
            "tower_ref", sheaveline.persistence.types.TowerRefColumn(length=16), nullable=False
        ),
        sa.Column("station", sheaveline.persistence.types.LengthColumn(), nullable=False),
        sa.Column(
            "crossarm_elevation", sheaveline.persistence.types.LengthColumn(), nullable=False
        ),
        sa.Column("offset_at_top", sheaveline.persistence.types.LengthColumn(), nullable=False),
        sa.CheckConstraint("station >= 0", name=op.f("ck_tower_shots_shot_station_not_negative")),
        sa.ForeignKeyConstraint(
            ["survey_id"],
            ["alignment_surveys.id"],
            name=op.f("fk_tower_shots_survey_id_alignment_surveys"),
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_tower_shots")),
        sa.UniqueConstraint("survey_id", "tower_ref", name="one_shot_a_tower"),
    )


def downgrade() -> None:
    op.drop_table("tower_shots")
    with op.batch_alter_table("alignment_surveys", schema=None) as batch_op:
        batch_op.drop_index("ix_alignment_by_machine")

    op.drop_table("alignment_surveys")
