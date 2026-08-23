// The vendored components call useTranslation().t for their labels. Supplying the module
// they already import keeps those files byte-identical to the original's, which is what
// makes the appearance comparison mean anything.
export function useTranslation() {
  return {
    t: (key: string, vars?: Record<string, unknown>) =>
      vars
        ? key.replace(/\{\{(\w+)\}\}/g, (_, name) => String(vars[name] ?? ""))
        : key,
  };
}
