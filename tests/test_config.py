from pathlib import Path

import pytest

from sheaveline.config import (
    DATABASE,
    DEFAULT_DATABASE,
    DEFAULT_HOST,
    DEFAULT_PORT,
    DEFAULT_RESORT,
    EVERY_NAME,
    HOST,
    JOURNAL,
    KEYS,
    PORT,
    RESORT,
    Configuration,
    ConfigurationError,
)
from sheaveline.journal import Level


def read(**env):
    return Configuration.from_environment(env)


def test_nothing_set_still_starts():
    made = read()
    assert made.database == DEFAULT_DATABASE
    assert made.resort == DEFAULT_RESORT
    assert made.journal is Level.INFO
    assert made.host == DEFAULT_HOST
    assert made.port == DEFAULT_PORT
    assert made.key_file is None


def test_every_setting_can_be_given():
    made = read(
        **{
            DATABASE: "sqlite+pysqlite:///office.sqlite3",
            RESORT: "Snowbasin",
            JOURNAL: "debug",
            KEYS: "/etc/sheaveline/keys.json",
            HOST: "0.0.0.0",
            PORT: "9000",
        }
    )
    assert made.database == "sqlite+pysqlite:///office.sqlite3"
    assert made.resort == "Snowbasin"
    assert made.journal is Level.DEBUG
    assert made.key_file == Path("/etc/sheaveline/keys.json")
    assert made.host == "0.0.0.0"
    assert made.port == 9000


def test_an_empty_value_counts_as_not_set():
    made = read(**{RESORT: "   ", PORT: "", KEYS: ""})
    assert made.resort == DEFAULT_RESORT
    assert made.port == DEFAULT_PORT
    assert made.key_file is None


def test_a_port_that_is_not_a_number():
    with pytest.raises(ConfigurationError, match=PORT):
        read(**{PORT: "eight thousand"})


def test_a_port_outside_the_range():
    for wrong in ("0", "65536", "-1"):
        with pytest.raises(ConfigurationError, match=PORT):
            read(**{PORT: wrong})


def test_a_journal_level_nobody_has():
    with pytest.raises(ConfigurationError, match="no such level"):
        read(**{JOURNAL: "chatty"})


def test_the_journal_can_be_switched_off_entirely():
    assert read(**{JOURNAL: "off"}).journal is Level.OFF


def test_an_in_memory_database_says_so():
    assert read(**{DATABASE: "sqlite+pysqlite:///:memory:"}).is_in_memory()
    assert not read().is_in_memory()


def test_a_server_with_no_key_file_says_so_rather_than_refusing_silently():
    made = read()
    assert not made.serves_keys()
    with pytest.raises(ConfigurationError, match="refuses every request"):
        made.require_keys()


def test_a_key_file_that_is_not_there(tmp_path):
    made = read(**{KEYS: str(tmp_path / "missing.json")})
    assert made.serves_keys()
    with pytest.raises(ConfigurationError, match="which is not there"):
        made.require_keys()


def test_a_key_file_that_is_there(tmp_path):
    wanted = tmp_path / "keys.json"
    wanted.write_text("[]")
    assert read(**{KEYS: str(wanted)}).require_keys() == wanted


def test_what_was_read_can_be_printed_without_leaking_anything(tmp_path):
    wanted = tmp_path / "keys.json"
    wanted.write_text('[{"secret": "do not print me"}]')
    described = dict(read(**{KEYS: str(wanted)}).described())
    assert described[KEYS] == str(wanted)
    assert "do not print me" not in " ".join(described.values())


def test_every_name_this_module_reads_is_listed():
    assert set(EVERY_NAME) == {DATABASE, RESORT, JOURNAL, KEYS, HOST, PORT}
    assert all(one.startswith("SHEAVELINE_") for one in EVERY_NAME)


def test_the_description_names_every_setting():
    described = dict(read().described())
    assert set(described) == set(EVERY_NAME)
    assert described[KEYS] == "not set"


def test_the_one_line_summary_says_where_the_records_are():
    printed = str(read(**{RESORT: "Snowbasin", PORT: "9000"}))
    assert "Snowbasin" in printed
    assert ":9000" in printed
    assert DEFAULT_DATABASE in printed


def test_the_configuration_says_whether_the_journal_was_actually_named():
    """Off and unset are the same Level and not the same answer."""
    assert not read().journal_given
    assert read(**{JOURNAL: "info"}).journal_given
    assert read(**{JOURNAL: "off"}).journal_given
    assert not read(**{JOURNAL: "   "}).journal_given


def test_an_unset_journal_still_reads_as_the_servers_default():
    assert read().journal is Level.INFO
    assert not read().journal_given
