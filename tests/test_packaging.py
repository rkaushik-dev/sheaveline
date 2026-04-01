"""What the package declares against what it actually imports.

A test suite structurally cannot catch a misfiled runtime dependency, because a
test run is the one environment where the dev extra is always installed. So this
reads the shipped source rather than importing it: it walks the syntax tree of
every module under src, collects the top level packages they import, and holds
that set against what pyproject declares.

The failure it exists to catch is the quiet one. Something under src imports a
library that is only in the dev extra, every test passes, and the first person
to install the package finds a command that cannot start.
"""

import ast
import sys
import tomllib
from pathlib import Path
from typing import Final

import pytest

ROOT: Final = Path(__file__).resolve().parents[1]
SOURCE: Final = ROOT / "src"
PACKAGE: Final = "sheaveline"

# Declared for the operator rather than imported by the package: alembic is run
# as a command against the migrations that ship with it, and uvicorn is the
# server the application is handed to. Adding to this list is a decision.
TOOL_DEPENDENCIES: Final = frozenset({"alembic", "uvicorn"})

# The import name is not always the distribution name.
IMPORT_NAMES: Final = {
    "sqlalchemy": "SQLAlchemy",
}


def project() -> dict:
    return tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))


def requirement_name(line: str) -> str:
    """The distribution name, with the extras marker and the pin taken off."""
    name = line.strip()
    for separator in ("==", ">=", "<=", "~=", ">", "<"):
        if separator in name:
            name = name.split(separator, 1)[0]
            break
    if "[" in name:
        name = name.split("[", 1)[0]
    return name.strip()


def declared_runtime() -> set[str]:
    return {requirement_name(one) for one in project()["project"]["dependencies"]}


def declared_dev() -> set[str]:
    extras = project()["project"]["optional-dependencies"]
    return {requirement_name(one) for one in extras["dev"]}


def shipped_modules() -> list[Path]:
    return sorted((SOURCE / PACKAGE).rglob("*.py"))


def imports_of(path: Path) -> set[str]:
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    found: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            found.update(alias.name.split(".")[0] for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
            found.add(node.module.split(".")[0])
    return found


def third_party_imports() -> dict[str, set[str]]:
    """Every top level import under src that is neither ours nor the standard library."""
    standard = sys.stdlib_module_names
    found: dict[str, set[str]] = {}
    for path in shipped_modules():
        for name in imports_of(path):
            if name == PACKAGE or name in standard or name.startswith("_"):
                continue
            found.setdefault(name, set()).add(str(path.relative_to(SOURCE)))
    return found


def distribution_for(import_name: str) -> str:
    return IMPORT_NAMES.get(import_name, import_name)


def test_the_package_ships_modules_at_all():
    assert len(shipped_modules()) > 50


def test_every_third_party_import_is_a_declared_runtime_dependency():
    declared = {one.lower() for one in declared_runtime()}
    for name, where in third_party_imports().items():
        assert distribution_for(name).lower() in declared, f"{name} imported by {where}"


def test_nothing_shipped_imports_something_only_in_the_dev_extra():
    dev_only = {one.lower() for one in declared_dev()} - {one.lower() for one in declared_runtime()}
    for name, where in third_party_imports().items():
        assert distribution_for(name).lower() not in dev_only, f"{name} imported by {where}"


def test_every_runtime_dependency_is_either_imported_or_a_named_tool():
    imported = {distribution_for(one).lower() for one in third_party_imports()}
    tools = {one.lower() for one in TOOL_DEPENDENCIES}
    for name in declared_runtime():
        assert name.lower() in imported or name.lower() in tools, name


def test_every_dependency_is_pinned_to_one_version():
    for line in project()["project"]["dependencies"]:
        assert "==" in line, line
    for line in project()["project"]["optional-dependencies"]["dev"]:
        assert "==" in line, line


def test_the_package_declares_the_python_it_needs():
    assert project()["project"]["requires-python"].startswith(">=3.")


def test_the_console_script_points_at_something_that_exists():
    scripts = project()["project"]["scripts"]
    assert "sheaveline" in scripts
    module, function = scripts["sheaveline"].split(":")
    path = SOURCE / Path(module.replace(".", "/")).with_suffix(".py")
    assert path.exists(), path
    tree = ast.parse(path.read_text(encoding="utf-8"))
    names = {node.name for node in ast.walk(tree) if isinstance(node, ast.FunctionDef)}
    assert function in names


def test_the_packages_are_found_under_src():
    assert project()["tool"]["setuptools"]["packages"]["find"]["where"] == ["src"]


def test_no_shipped_module_imports_a_test_helper():
    for path in shipped_modules():
        for name in imports_of(path):
            assert name not in {"pytest", "hill", "wire"}, path


def test_no_shipped_module_reaches_back_into_the_tests():
    for path in shipped_modules():
        text = path.read_text(encoding="utf-8")
        assert "from tests" not in text, path
        assert "import tests" not in text, path


@pytest.mark.parametrize("name", sorted(third_party_imports()))
def test_each_third_party_import_names_a_real_distribution(name):
    """A typo in an import is a runtime failure nothing else here would catch."""
    assert distribution_for(name).lower() in {one.lower() for one in declared_runtime()}


def test_the_migrations_ship_beside_the_package():
    assert (ROOT / "alembic.ini").exists()
    assert (ROOT / "migrations" / "env.py").exists()
    assert list((ROOT / "migrations" / "versions").glob("*.py"))
