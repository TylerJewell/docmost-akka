import HistoryItem from "@/features/page-history/components/history-item";
import { ScrollArea, Divider, Group, Button } from "@mantine/core";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { usePageHistoryStream } from "@/features/page-history/hooks/use-page-history-stream";

interface Props {
  pageId: string;
}

/**
 * The original's list, with its data layer replaced.
 *
 * <p>Where the original paginates a fetched query and refetches, this subscribes to the
 * server's stream of versions for the page (RENDERING.md R1). Everything below the data
 * boundary — the item component, the scroll area, the button row — is the original's.
 *
 * <p>Compare and restore are the original's and are not in this port's slice, so the controls
 * that reach them are not wired: this port decides when a version is kept, not what is done
 * with one afterwards. Their absence is declared in the README's difference list.
 */
function HistoryList({ pageId }: Props) {
  const { t } = useTranslation();
  const { items, status } = usePageHistoryStream(pageId);
  const [activeHistoryId, setActiveHistoryId] = useState<string | null>(null);

  const handleSelect = useCallback((id: string) => setActiveHistoryId(id), []);
  const noop = useCallback(() => {}, []);

  const activeId = activeHistoryId ?? items[0]?.id ?? null;

  if (status === "loading") {
    return <></>;
  }

  if (status === "error") {
    return <div>{t("Error loading page history.")}</div>;
  }

  if (items.length === 0) {
    return <>{t("No page history saved yet.")}</>;
  }

  return (
    <div>
      <ScrollArea h={620} w="100%" type="scroll" scrollbarSize={5}>
        {items.map((historyItem, index) => (
          <HistoryItem
            key={historyItem.id}
            historyItem={historyItem}
            index={index}
            onSelect={handleSelect}
            isActive={historyItem.id === activeId}
            compareMode={false}
            isChecked={false}
            isCheckboxDisabled={false}
            canCompare={false}
            onToggleCompare={noop}
            onStartCompare={noop}
          />
        ))}
      </ScrollArea>

      <Divider />
      <Group p="xs" wrap="nowrap">
        <Button variant="default" size="compact-md">
          {t("Cancel")}
        </Button>
        <Button size="compact-md">{t("Restore")}</Button>
      </Group>
    </div>
  );
}

export default HistoryList;
