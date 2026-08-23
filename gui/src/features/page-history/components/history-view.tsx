import { ScrollArea, Text, Title } from "@mantine/core";
import classes from "./css/history.module.css";
import { usePageHistoryStream } from "@/features/page-history/hooks/use-page-history-stream";

interface Props {
  pageId: string;
}

/**
 * Renders the newest version's content. The original renders it through the full editor with
 * a diff against the previous version; diffing is part of the compare feature, which is not
 * in this port's slice, so this shows the content plainly.
 */
export default function HistoryView({ pageId }: Props) {
  const { items } = usePageHistoryStream(pageId);
  const version = items[0];

  return (
    <ScrollArea h={700} type="scroll">
      <div className={classes.sidebarRightSection}>
        <Title order={1}>{version?.title ?? ""}</Title>
        {(version?.paragraphs ?? []).map((paragraph, index) => (
          <Text key={index} mt="md">
            {paragraph}
          </Text>
        ))}
      </div>
    </ScrollArea>
  );
}
