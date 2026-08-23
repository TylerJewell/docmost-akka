import { Modal, Text } from "@mantine/core";
import HistoryModalBody from "@/features/page-history/components/history-modal-body";
import { useTranslation } from "react-i18next";

interface Props {
  pageId: string;
}

/** The original's modal shell, at the size the original uses for the wide layout. */
export default function HistoryModal({ pageId }: Props) {
  const { t } = useTranslation();

  return (
    <Modal.Root size={1400} opened onClose={() => {}} aria-label={t("Page history")}>
      <Modal.Overlay />
      <Modal.Content style={{ overflow: "hidden" }}>
        <Modal.Header>
          <Modal.Title>
            <Text size="md" fw={500}>
              {t("Page history")}
            </Text>
          </Modal.Title>
          <Modal.CloseButton aria-label={t("Close")} />
        </Modal.Header>
        <Modal.Body>
          <HistoryModalBody pageId={pageId} />
        </Modal.Body>
      </Modal.Content>
    </Modal.Root>
  );
}
