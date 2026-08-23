import HistoryList from "@/features/page-history/components/history-list";
import HistoryView from "@/features/page-history/components/history-view";
import classes from "./css/history.module.css";

interface Props {
  pageId: string;
}

/**
 * The original's two-pane layout: the version list on the left, the selected version's
 * content on the right. The compare banner and the change-navigation controls belong to the
 * compare and restore features, which are outside this port's slice.
 */
export default function HistoryModalBody({ pageId }: Props) {
  return (
    <div className={classes.sidebarFlex}>
      <nav className={classes.sidebar}>
        <div className={classes.sidebarMain}>
          <HistoryList pageId={pageId} />
        </div>
      </nav>

      <div style={{ position: "relative", flex: 1 }}>
        <HistoryView pageId={pageId} />
      </div>
    </div>
  );
}
