interface EmptyStateProps {
  message: string
  action?: React.ReactNode
}

/** 데이터가 없을 때 카드/섹션에 표시하는 공용 빈 상태. */
export default function EmptyState({ message, action }: EmptyStateProps): React.JSX.Element {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-lg bg-muted/50 px-4 py-8 text-center">
      <p className="text-sm text-muted-foreground">{message}</p>
      {action}
    </div>
  )
}
