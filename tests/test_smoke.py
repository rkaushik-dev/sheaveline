"""The things that only break outside a test run, checked inside one where we can.

Three failures this repository has actually had, none of which the ordinary
tests could see. A dependency filed under the dev extra, so the command would
not start once the wheel was installed on its own. A module that imported a test
helper, which works until the tests are not on the path. And an entry point
naming a function that had been renamed.

The wheel build is in the CI job because it needs a clean environment. What is
here is everything that can be proved without one, so a laptop finds it before a
runner does.
"""

import ast
import importlib
import os
import pathlib
import subprocess
import sys
import tomllib

import pytest
from click.testing import CliRunner

from sheaveline import __version__
from sheaveline.cli import cli

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "src" / "sheaveline"


def manifest():
    return tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))


def test_the_console_script_points_at_something_that_exists():
    target = manifest()["project"]["scripts"]["sheaveline"]
    module, _, function = target.partition(":")
    assert getattr(importlib.import_module(module), function)


def test_the_version_the_package_reports_is_the_one_it_is_built_as():
    assert manifest()["project"]["version"] == __version__


def test_every_runtime_dependency_is_pinned_to_one_version():
    """A range is a promise that a future release will not break the office."""
    for one in manifest()["project"]["dependencies"]:
        assert "==" in one, one
        assert not any(mark in one for mark in (">=", "<=", "~=", "*")), one


def test_the_program_starts_and_says_its_own_version():
    reply = CliRunner().invoke(cli, ["--version"])
    assert reply.exit_code == 0
    assert __version__ in reply.output


def test_every_command_group_answers_help_without_a_database():
    """Help is the one thing that must work before anything is set up."""
    for name in cli.commands:
        reply = CliRunner().invoke(cli, [name, "--help"])
        assert reply.exit_code == 0, name
        assert f"Usage: cli {name}" in reply.output, name


def test_no_shipped_module_reaches_for_a_test_helper():
    forbidden = {"hill", "wire", "office", "pytest", "_pytest"}
    for path in SRC.rglob("*.py"):
        for node in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
            if isinstance(node, ast.Import):
                names = [one.name.split(".")[0] for one in node.names]
            elif isinstance(node, ast.ImportFrom):
                names = [(node.module or "").split(".")[0]]
            else:
                continue
            assert not forbidden & set(names), f"{path.name}: {names}"


def test_every_module_imports_on_its_own():
    """A module that only imports because something else imported it first."""
    modules = sorted(
        ".".join(one.relative_to(SRC.parent).with_suffix("").parts)
        for one in SRC.rglob("*.py")
        if one.name != "__init__.py"
    )
    assert len(modules) > 100
    script = "import importlib\n" + "\n".join(
        f"importlib.import_module({one!r})" for one in modules
    )
    reply = subprocess.run(
        [sys.executable, "-c", script],
        capture_output=True,
        text=True,
        cwd=ROOT,
        env={**os.environ, "PYTHONPATH": str(SRC.parent)},
    )
    assert reply.returncode == 0, reply.stderr


@pytest.mark.parametrize("name", ["setup", "tables", "settings", "guide"])
def test_the_commands_that_run_before_anything_is_set_up(tmp_path, name):
    url = f"sqlite+pysqlite:///{tmp_path / 'fresh.sqlite3'}"
    reply = CliRunner(mix_stderr=False).invoke(cli, ["--database", url, name])
    assert reply.exit_code == 0, reply.stderr


def test_check_refuses_a_database_that_has_never_been_set_up(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'fresh.sqlite3'}"
    reply = CliRunner(mix_stderr=False).invoke(cli, ["--database", url, "check"])
    assert reply.exit_code == 1
    assert "run setup" in reply.stderr


def test_setup_is_safe_to_run_again(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'fresh.sqlite3'}"
    runner = CliRunner(mix_stderr=False)
    first = runner.invoke(cli, ["--database", url, "setup"])
    second = runner.invoke(cli, ["--database", url, "setup"])
    assert first.exit_code == 0
    assert second.exit_code == 0
    assert first.output == second.output


def test_the_openapi_document_builds_without_dropping_anything():
    """A default pydantic cannot encode vanishes from the document with a warning."""
    import warnings

    from sheaveline.api.app import Settings, build_app

    with warnings.catch_warnings():
        warnings.simplefilter("error")
        document = build_app(Settings.in_memory()).openapi()
    paths = sorted(document["paths"])
    assert len(paths) > 30
    assert "/health" in paths
    assert "/ready" in paths


def test_the_document_names_the_default_a_client_would_actually_send():
    from sheaveline.api.app import Settings, build_app

    document = build_app(Settings.in_memory()).openapi()
    field = document["components"]["schemas"]["ObservationIn"]["properties"]["visibility"]
    assert field["type"] == "string"
    assert field["default"]
    assert "30000" in field["default"]


def test_every_route_is_filed_under_a_tag():
    from sheaveline.api.app import Settings, build_app

    document = build_app(Settings.in_memory()).openapi()
    for path, methods in document["paths"].items():
        for verb, operation in methods.items():
            assert operation.get("tags"), f"{verb} {path}"
