# docmost-akka

Decides who may edit a page, when an edit is written to it, and when a version of it is kept.

A port of [docmost/docmost](https://github.com/docmost/docmost) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

docmost is a collaborative wiki: people edit the same page at the same time in a browser, and
the server keeps the page and a history of it. It was ported to derive a specification format
precise enough to regenerate a system on a different stack — the port is the vehicle, the
specification is the deliverable.

The specifications the port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness) under
`docmost-port/`.

---

## docmost/docmost → this port

📉 1,212 TypeScript lines → **1,383 Java and TypeScript lines**<br>
📁 14 files → **31 files**<br>
🖥️ 3 processes → **1 process**<br>
💾 713 MB container image → **298 MB of libraries plus a 68 KB program**<br>
⚡ 7.6 → **2.5** seconds, cold start<br>
🎯 matching answers 73 of 73 → **73 of 73**<br>
⏱️ 0.44 → **1.35** milliseconds to decide who may edit a page

Full method and the numbers that did *not* make this list:
[`../docmost-port/bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/docmost-port/bench/REPORT.md).

---

## What it took to build

⏱️ **2.4 hours** from the first command to the published repository, **2.4** of them active<br>
💬 **683** exchanges with the model<br>
✍️ **513,497** tokens written by the model, **196,338,775** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **97** tests

```bash
python toolkit/tokens.py --port docmost    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

- **A page can be reached only by someone who belongs to the space holding it.** Being named
  on the page itself does not let a stranger in.
- **A page can be locked, and so can any page above it.** To reach a page you must be named on
  every locked page on the way down to it; missing from one of them and you are turned away,
  however much access you were given elsewhere.
- **The nearest lock decides whether you can type, and the strongest one does not.** Named as
  an editor on a page's grandparent but only as a reader on the page itself, and you get to
  read it. The other way round, and you get to edit it.
- **A page in the wastebasket can be read and not changed**, by anybody, whatever they were
  allowed before.
- **Saving a page that already holds exactly what is being saved does nothing at all** — the
  page is not touched, nobody is told, and no version is kept.
- **The people who typed are remembered on the page for good**, and the person who created it
  is always among them.
- **After the first change, a clock starts, and it does not restart.** Six more changes in the
  next minute are all folded into the same wait; the version is kept when the clock runs out,
  counted from the first change and not the last.
- **The wait is one minute for a page less than five minutes old, and five minutes after
  that.**
- **When the clock runs out, a version is kept unless there is nothing new to keep** — either
  the page still holds what the last version holds, or the page has never had a version and is
  empty.
- **A version remembers who typed since the last one**, which is a different list from the one
  the page keeps.

---

## Design decisions

**Leading-edge window.** A burst of typing should produce one saved version rather than one per
keystroke, and the wait is counted from the first change so a person who keeps typing still
gets a version kept. Somebody editing for an hour gets a version every five minutes instead of
none until they stop.

**The page holds its own deadline.** The tool that would normally hold a deadline moves it
every time you hand it a new one, which is the opposite of what is wanted here. Keeping the
deadline on the page means a later change simply finds one already set and leaves it alone.

**Refusing an edit rather than dropping it.** Somebody who is only allowed to read gets told
so, instead of watching their typing vanish. An answer that says no is something a program can
act on; silence looks exactly like success.

**Two lists of who typed.** The page keeps everyone who ever contributed and the version keeps
only the people who typed since the last one, because they answer different questions — who
worked on this page, and who made this particular change.

**The page's own screen, not a new one.** The list of versions is docmost's own, file for file,
pointed at this port instead. Anyone can then see whether the rebuild looks the same as what it
replaced, which they could not if it had been drawn again from scratch.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/docmost-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9067.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once
- Node 20 or newer, for the page-history screen

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9067**.

### Start the screen

```bash
cd gui
npm install
npm run dev
```

Then open http://localhost:5173/?pageId=your-page-id.

### Try it

```bash
# Somebody who may edit pages in this space
curl -X PUT localhost:9067/spaces/space-1/members \
  -H 'Content-Type: application/json' \
  -d '{"userId":"ada","roles":["WRITER"],"groupIds":[]}'

# A page
curl -X POST localhost:9067/pages/notes \
  -H 'Content-Type: application/json' \
  -d '{"parentPageId":null,"spaceId":"space-1","creatorId":"ada","createdAt":"2026-03-01T12:00:00Z"}'

# May Ada edit it?
curl localhost:9067/pages/notes/session/ada

# Save something
curl -X POST localhost:9067/pages/notes/store \
  -H 'Content-Type: application/json' \
  -d '{"content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hello"}]}]},"editorIds":["ada"],"userId":"ada"}'

# Watch the versions arrive
curl -N localhost:9067/pages/notes/versions/stream
```

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `VITE_API_BASE` | `http://localhost:9067` | where the screen looks for the service |

The service calls no model provider and needs no keys.

---

## Where it differs from docmost

Everything not listed here behaves the same way on purpose, including the parts that look like
mistakes.

- **The screen is told about new versions instead of asking for them.** docmost's list of
  versions asks the server again each time it needs them. This port's list is sent them as
  they are kept, because a version appears without anybody doing anything and a list that only
  asks shows the wrong thing until it next asks. Three things change as a result: what happens
  while the connection is down, the order changes are seen in, and the longest a change can go
  unseen. docmost never had to answer the first of those, so this port was given an answer —
  when the connection comes back, whatever the server holds replaces what the screen was
  showing, which cannot double up or lose a version the way resuming from a position could.
  Measured: it catches up in 0.51 seconds.
- **Only one person can save a page at a time.** docmost lets several saves of the same page
  proceed together and relies on a database lock to sort them out. This port handles one at a
  time per page, because the rules about what a save changes all read the page's current state
  first and doing that under a lock is the same guarantee with fewer moving parts. Nobody has
  measured what this costs when many people edit one page at once — `not checked`.
- **Keeping a version and clearing the list of who typed happen together.** docmost takes the
  list out of one store and writes the version to another, and puts the list back if the write
  fails. This port does both in one step, because they are one fact about the page and a
  failure between them would lose the names.
- **Every version a page has ever had is kept with the page itself.** docmost keeps them in a
  separate table with no limit. This port holds them alongside the page, which is simpler and
  makes the two above possible, and means a page with a great many long versions will
  eventually hit a size limit the original does not have. Where that limit falls has
  `not been measured`.
- **A page's ancestors are looked up one at a time.** docmost resolves the whole chain in a
  single database query. This port reads each page in turn, because each is a separate thing
  with its own identity here. Measured: 1.35 milliseconds against 0.44, and the gap grows with
  how deeply the page is nested.
- **An edit from somebody who may only read is refused, out loud.** docmost's connection
  handler marks the session read-only and its own code never sees such an edit, so it has no
  answer to copy. This port answers "forbidden" and says why, because a rejected edit that
  produces no answer is indistinguishable from one that worked.
- **Someone's name on a version is their identifier.** docmost shows a display name and a
  picture, from a directory of people. This port has no such directory, because who people are
  is a different job from deciding when a version is kept.
- **Comparing two versions and putting an old one back are not here.** docmost offers both from
  the same screen. This port decides when a version is kept and does nothing with one
  afterwards.
- **Whether a pending wait survives the service restarting** has `not been checked`. docmost's
  wait lives in a store outside the application and survives one; whether this port's does is a
  property of the platform's own timers, and no test has restarted it mid-wait.
- **The order of names on a version** is the order they typed in, on both sides — but docmost
  reads that order out of a store that does not promise one, so on a busy page the two may
  disagree. `not checked` beyond the sequences in the benchmark, which agree.

---

## Licence

docmost is licensed under the GNU Affero General Public License version 3. This port is a
derived work — three of docmost's own interface files are included unchanged — and is therefore
also AGPL-3.0. See [`ACKNOWLEDGEMENTS.md`](ACKNOWLEDGEMENTS.md).
