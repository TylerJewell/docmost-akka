import { useEffect, useRef, useState } from "react";
import { IPageHistory } from "@/features/page-history/types/page.types";

const API = import.meta.env.VITE_API_BASE || "http://localhost:9067";
const RECONNECT_DELAY_MS = 500;

type Status = "loading" | "ready" | "error";

/**
 * Subscribes to a page's versions rather than fetching them.
 *
 * <p>RENDERING.md R1: the view shows state the server owns and which changes without anyone
 * touching the page — a history window closing keeps a version — so it subscribes rather than
 * asking again on a timer. The first message carries every version the page already has, so
 * the first render needs no second round trip (R1.4).
 *
 * <p>R1.3 is the half that gets skipped, so it is the half written here: on a dropped stream
 * the hook reconnects and replaces its list wholesale from the reconnect's first message. The
 * original has no behaviour to copy — it polls a paginated query and a poller never has to
 * answer this — so the port is given one: the server's state on reconnect wins, which cannot
 * duplicate or lose a version the way replaying from a position could.
 */
export function usePageHistoryStream(pageId: string) {
  const [items, setItems] = useState<IPageHistory[]>([]);
  const [status, setStatus] = useState<Status>("loading");
  const closed = useRef(false);

  useEffect(() => {
    closed.current = false;
    let source: EventSource | null = null;
    let retry: ReturnType<typeof setTimeout> | null = null;

    const connect = () => {
      if (closed.current) return;
      source = new EventSource(`${API}/pages/${pageId}/versions/stream`);

      source.onopen = () => {
        // Read by the capture script to record that the stream was open across the idle
        // window; nothing in the view depends on it.
        (window as any).__streamOpen = true;
      };

      source.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          // Newest first, as the original's list is ordered.
          setItems(payload.versions ?? []);
          setStatus("ready");
        } catch {
          setStatus("error");
        }
      };

      source.onerror = () => {
        (window as any).__streamOpen = false;
        source?.close();
        if (closed.current) return;
        retry = setTimeout(connect, RECONNECT_DELAY_MS);
      };
    };

    connect();

    return () => {
      closed.current = true;
      if (retry) clearTimeout(retry);
      source?.close();
    };
  }, [pageId]);

  return { items, status };
}
