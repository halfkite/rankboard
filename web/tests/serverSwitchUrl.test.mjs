import assert from "node:assert/strict";
import test from "node:test";
import { serverSwitchUrl } from "../src/serverSwitchUrl.ts";

test("switching to a peer replaces server and preserves page preferences", () => {
  const url = new URL(serverSwitchUrl(
    "http://127.0.0.1:8765/?server=peer-b",
    "http://127.0.0.1:8765/?lang=zh&layout=tile&server=peer-a"
  ));
  assert.equal(url.searchParams.get("server"), "peer-b");
  assert.equal(url.searchParams.get("lang"), "zh");
  assert.equal(url.searchParams.get("layout"), "tile");
  assert.equal(url.searchParams.getAll("server").length, 1);
});

test("switching back to the host removes the server parameter", () => {
  const url = new URL(serverSwitchUrl(
    "http://127.0.0.1:8765/",
    "http://127.0.0.1:8765/?lang=en&server=peer-b"
  ));
  assert.equal(url.searchParams.has("server"), false);
  assert.equal(url.searchParams.get("lang"), "en");
});

test("switching to a separately hosted server keeps that origin", () => {
  const url = new URL(serverSwitchUrl(
    "https://example.net:8765/",
    "http://127.0.0.1:8765/?lang=zh&server=peer-a"
  ));
  assert.equal(url.origin, "https://example.net:8765");
  assert.equal(url.searchParams.has("server"), false);
  assert.equal(url.searchParams.get("lang"), "zh");
});
