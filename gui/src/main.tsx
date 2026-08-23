import React from "react";
import ReactDOM from "react-dom/client";
import { MantineProvider } from "@mantine/core";
import "@mantine/core/styles.css";
import HistoryModal from "@/features/page-history/components/history-modal";

const pageId = new URLSearchParams(location.search).get("pageId") ?? "demo-page";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <MantineProvider>
      <HistoryModal pageId={pageId} />
    </MantineProvider>
  </React.StrictMode>,
);
