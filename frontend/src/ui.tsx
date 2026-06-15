import type { ReactNode } from "react";

export function SkipLink() {
  return (
    <a className="skip-link" href="#main-content">
      Skip to main content
    </a>
  );
}

export function PageIntro({
  description,
  id,
  title
}: {
  description: string;
  id?: string;
  title: string;
}) {
  return (
    <header className="page-intro">
      <h1 id={id}>{title}</h1>
      <p className="lede">{description}</p>
    </header>
  );
}

export function EmptyState({
  actionHref,
  actionLabel,
  children,
  message,
  onAction,
  title
}: {
  actionHref?: string;
  actionLabel?: string;
  children?: ReactNode;
  message: string;
  onAction?: () => void;
  title: string;
}) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon" aria-hidden="true">
        ◌
      </div>
      <h3>{title}</h3>
      <p>{message}</p>
      {children}
      {actionLabel && actionHref && (
        <a className="secondary-action empty-state-action" href={actionHref}>
          {actionLabel}
        </a>
      )}
      {actionLabel && onAction && (
        <button className="secondary-action empty-state-action" type="button" onClick={onAction}>
          {actionLabel}
        </button>
      )}
    </div>
  );
}

export function LoadingList({ count = 3 }: { count?: number }) {
  return (
    <div aria-busy="true" aria-label="Loading" className="loading-list" role="status">
      {Array.from({ length: count }, (_, index) => (
        <div className="skeleton-card" key={index}>
          <span className="skeleton-block skeleton-avatar" />
          <span className="skeleton-block skeleton-line wide" />
          <span className="skeleton-block skeleton-line" />
        </div>
      ))}
    </div>
  );
}

export function FieldHint({ children }: { children: ReactNode }) {
  return <p className="field-hint">{children}</p>;
}

export function connectionLabel(connection: string) {
  switch (connection) {
    case "connected":
      return "Live";
    case "connecting":
      return "Connecting";
    case "closed":
      return "Offline";
    default:
      return "Idle";
  }
}
