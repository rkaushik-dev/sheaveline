"""The command line the office works at."""

# Importing a command module is what puts its commands on the group.
from sheaveline.cli import (  # noqa: E402,F401  registered by import
    board,
    crew,
    day,
    drawing,
    evac,
    plant,
    report,
    rope,
    upkeep,
)
from sheaveline.cli.main import Console, cli, run

__all__ = [
    "Console",
    "cli",
    "run",
]
