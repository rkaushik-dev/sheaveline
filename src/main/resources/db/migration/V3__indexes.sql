-- Index the questions the office asks that were reading whole tables: what is still open, what
-- has not gone to the board, and everything on one machine or since one moment.

CREATE INDEX ix_defects_still_open ON defects (cleared, raised);

CREATE INDEX ix_filings_by_machine ON filings (lift_code, filed);

CREATE INDEX ix_holds_by_machine ON holds (lift_code, started);

CREATE INDEX ix_incidents_not_yet_filed ON incidents (filing_number, at);

CREATE INDEX ix_observations_by_moment ON observations (taken);

CREATE INDEX ix_stops_still_open ON stops (restarted, stopped);

CREATE INDEX ix_work_orders_open_by_machine ON work_orders (lift_code, closed);
