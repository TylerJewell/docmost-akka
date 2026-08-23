# Acknowledgements

This project is a port of **[docmost/docmost](https://github.com/docmost/docmost)** —
specifically of its collaboration server's decisions about who may edit a page, when an edit is
written to it, and when a version of it is kept.

## Licence

**docmost core is licensed under the GNU Affero General Public License, version 3**, read from
the repository's own `LICENSE` file rather than from a badge. Its `README.md` adds that
Enterprise Edition files — everything under `apps/server/src/ee`, `apps/client/src/ee` and
`packages/ee` — are under a separate Docmost Enterprise licence instead.

Every file this port read or copied was checked against that boundary, and **none of them is
under `ee/`**:

| From docmost | Under `ee/`? |
|---|---|
| `apps/client/src/features/page-history/components/history-item.tsx` | no — AGPL-3.0 |
| `apps/client/src/features/page-history/components/css/history.module.css` | no — AGPL-3.0 |
| `apps/client/src/components/ui/custom-avatar.tsx` | no — AGPL-3.0 |
| `apps/server/src/collaboration/**` | no — AGPL-3.0 |
| `apps/server/src/database/repos/page/page-permission.repo.ts` | no — AGPL-3.0 |
| `apps/server/src/database/repos/space/utils.ts` | no — AGPL-3.0 |

**This project is therefore AGPL-3.0**, and its `LICENSE` is that licence. Copied material
carries its licence with it, and files were copied — see below. The scaffold's default licence
does not survive that.

## What was copied verbatim

Three files, all under `gui/src/`, and all reproduced **byte for byte**. They are copied
deliberately: `RENDERING.md` R3 has a port reuse the interface the source already has, and the
appearance comparison in `../docmost-port/gui/` only means something if the components being
compared are the original's rather than a rewrite of them.

| File in this project | Copied from docmost | Changed? |
|---|---|---|
| `gui/src/features/page-history/components/history-item.tsx` | `apps/client/src/features/page-history/components/history-item.tsx` | no — `diff` reports no change |
| `gui/src/features/page-history/components/css/history.module.css` | `apps/client/src/features/page-history/components/css/history.module.css` | no — `diff` reports no change |
| `gui/src/components/ui/custom-avatar.tsx` | `apps/client/src/components/ui/custom-avatar.tsx` | no |

The imports in those files are satisfied by small local modules and a Vite alias rather than by
editing the files, precisely so that they can stay identical.

`gui/src/features/page-history/components/history-modal.tsx`, `history-modal-body.tsx`,
`history-list.tsx` and `history-view.tsx` are **derived** from docmost's files of the same
names: the layout, the class names and the control arrangement are the original's, and the data
layer is rewritten to subscribe to a stream. They are not byte-identical and are not claimed to
be.

## Strings that occur in both, and why

`python toolkit/copied_strings.py docmost` finds six literals of ten characters or more that
appear in both this project and docmost. None was copied; each is a phrase two systems
describing the same domain arrive at independently, and each was checked against where it
actually occurs in docmost rather than assumed.

| Literal | In docmost | Verdict |
|---|---|---|
| `page not found` | `persistence.extension.ts:66`, as a log line | Independent. Here it is an entity error for a command naming a page that does not exist. Two systems both call an absent page that. |
| `page already exists` | **nowhere** — the tool matched it against a different string | Not shared at all. |
| `page-created`, `page-deleted` | `queue.constants.ts:47,53`, as queue job names | Independent. Here they are `@TypeName` values on persisted events. Both are the obvious past-tense spelling of the same fact; neither was read from the other. |
| `restricted` | `permission.ts:24`, as the `PageAccessLevel` enum value | The vocabulary of the domain being ported. docmost calls a page with a permission list restricted; a port of that rule that called it something else would be harder to read against the original. |
| `deleted-page` | `page.controller.ts:41`, in an import path | Coincidence. Here it is a test's entity id. |

## Behaviour derived without text being copied

Nearly all of it, and it is the point rather than something to be coy about. The rules in
`../docmost-port/specs/SPEC-001-docmost.md` are docmost's rules: the ancestor-chain permission
resolution, the leading-edge history window and its two spans, the content-equality skip, the
contributor accumulation, and the two routes by which a closing window keeps nothing. Every one
of them was established by running docmost — see `../docmost-port/docs/question-log.md` — and
reproduced deliberately.

Where docmost has no settled answer, the port was given one rather than inventing an agreement:
those are listed as open decisions in the specification and in the README's
`Where it differs from docmost` list.

## Also used

- [Akka SDK](https://doc.akka.io/) 3.6.3 — the platform this is rebuilt on.
- [Mantine](https://mantine.dev/) — the component library docmost's interface uses, and which
  the vendored components require.
