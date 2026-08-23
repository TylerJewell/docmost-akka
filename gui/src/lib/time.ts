// The original's formattedDate, without its i18n and locale indirection: the vendored
// history item calls this and nothing else in this file.
import { isToday, isYesterday, format } from "date-fns";

export function formattedDate(date: Date) {
  if (isToday(date)) {
    return `Today, ${format(date, "h:mmaaa").replace("am", "AM").replace("pm", "PM")}`;
  } else if (isYesterday(date)) {
    return `Yesterday, ${format(date, "h:mmaaa").replace("am", "AM").replace("pm", "PM")}`;
  }
  return format(date, "MMM dd, yyyy, h:mmaaa").replace("am", "AM").replace("pm", "PM");
}
