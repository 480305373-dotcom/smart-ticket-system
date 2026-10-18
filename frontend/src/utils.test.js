import test from "node:test";
import assert from "node:assert/strict";
import { date } from "./utils.js";
test("UTC events and day boundaries display in Shanghai time", () => {
  assert.equal(date("2026-10-18T08:24:55.487Z"), "2026-10-18 16:24:55");
  assert.equal(date("2026-10-18T18:30:00Z"), "2026-10-19 02:30:00");
});
test("explicit offsets and existing local database times agree", () => {
  assert.equal(date("2026-10-18T16:24:55+08:00"), "2026-10-18 16:24:55");
  assert.equal(date("2026-10-18T16:24:55.487"), "2026-10-18 16:24:55");
});
test("missing timestamps keep an empty-state label", () => {
  assert.equal(date(null), "—");
  assert.equal(date(""), "—");
});
