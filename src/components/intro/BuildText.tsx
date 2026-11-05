/**
 * Text that the cursor swarm types in.
 * The engine only touches the inner spans' textContent; React renders them once
 * and never changes them again while `text` stays the same.
 */
export function BuildText({ text, className }: { text: string; className?: string }) {
  return (
    <span data-build="type" data-text={text} className={className}>
      <span data-typed="" />
      <span data-caret="" className="bt-caret" />
      <span data-ghost="" className="bt-ghost">{text}</span>
    </span>
  );
}
