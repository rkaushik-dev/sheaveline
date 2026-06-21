"""index the six questions the office asks that were reading whole tables

Revision ID: 0003
Revises: 0002
Created: 2026-09-05 08:35:15.446946
"""

from collections.abc import Sequence

from alembic import op

import sheaveline.persistence.types  # noqa: F401  the columns are named in full

revision: str = "0003"
down_revision: str | None = "0002"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    with op.batch_alter_table("defects", schema=None) as batch_op:
        batch_op.create_index("ix_defects_still_open", ["cleared", "raised"], unique=False)

    with op.batch_alter_table("filings", schema=None) as batch_op:
        batch_op.create_index("ix_filings_by_machine", ["lift_code", "filed"], unique=False)

    with op.batch_alter_table("holds", schema=None) as batch_op:
        batch_op.create_index("ix_holds_by_machine", ["lift_code", "started"], unique=False)

    with op.batch_alter_table("incidents", schema=None) as batch_op:
        batch_op.create_index("ix_incidents_not_yet_filed", ["filing_number", "at"], unique=False)

    with op.batch_alter_table("observations", schema=None) as batch_op:
        batch_op.create_index("ix_observations_by_moment", ["taken"], unique=False)

    with op.batch_alter_table("stops", schema=None) as batch_op:
        batch_op.create_index("ix_stops_still_open", ["restarted", "stopped"], unique=False)

    with op.batch_alter_table("work_orders", schema=None) as batch_op:
        batch_op.create_index(
            "ix_work_orders_open_by_machine", ["lift_code", "closed"], unique=False
        )


def downgrade() -> None:
    with op.batch_alter_table("work_orders", schema=None) as batch_op:
        batch_op.drop_index("ix_work_orders_open_by_machine")

    with op.batch_alter_table("stops", schema=None) as batch_op:
        batch_op.drop_index("ix_stops_still_open")

    with op.batch_alter_table("observations", schema=None) as batch_op:
        batch_op.drop_index("ix_observations_by_moment")

    with op.batch_alter_table("incidents", schema=None) as batch_op:
        batch_op.drop_index("ix_incidents_not_yet_filed")

    with op.batch_alter_table("holds", schema=None) as batch_op:
        batch_op.drop_index("ix_holds_by_machine")

    with op.batch_alter_table("filings", schema=None) as batch_op:
        batch_op.drop_index("ix_filings_by_machine")

    with op.batch_alter_table("defects", schema=None) as batch_op:
        batch_op.drop_index("ix_defects_still_open")
