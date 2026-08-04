import type { Sentiment, SentimentLabel } from '@renderer/types/console'

/**
 * 채팅 감정 8종 표시 규칙.
 *
 * 백엔드는 8종 비율을 전부 내려주고 상위 몇 개를 보여줄지는 프론트가 정한다.
 * 좁은 콘솔 사이드 패널에서 8줄은 읽히지 않으므로 상위 3종만 노출한다.
 */

const LABEL_TEXT: Record<SentimentLabel, string> = {
  JOY: '웃음/재미',
  EXCITEMENT: '환호/감탄',
  SUPPORT: '응원/호감',
  SURPRISE: '놀람',
  SADNESS: '슬픔/아쉬움',
  ANGER: '분노/불만',
  CONFUSION: '혼란/질문',
  NEUTRAL: '중립'
}

/**
 * 감정을 긍정·중립·부정 색으로 묶는다.
 * 8종마다 색을 따로 두면 구분이 안 되고, 스트리머가 알고 싶은 건 분위기의 방향이다.
 */
const LABEL_TONE: Record<SentimentLabel, 'positive' | 'neutral' | 'negative'> = {
  JOY: 'positive',
  EXCITEMENT: 'positive',
  SUPPORT: 'positive',
  SURPRISE: 'neutral',
  CONFUSION: 'neutral',
  NEUTRAL: 'neutral',
  SADNESS: 'negative',
  ANGER: 'negative'
}

const TONE_BAR: Record<'positive' | 'neutral' | 'negative', string> = {
  positive: 'bg-success',
  neutral: 'bg-muted-foreground/40',
  negative: 'bg-destructive'
}

export function sentimentLabelText(label: SentimentLabel): string {
  return LABEL_TEXT[label] ?? label
}

export function sentimentBarClass(label: SentimentLabel): string {
  return TONE_BAR[LABEL_TONE[label] ?? 'neutral']
}

export interface SentimentShare {
  label: SentimentLabel
  text: string
  /** 0~1 비율 */
  ratio: number
  barClass: string
}

/** 비율 상위 N개를 큰 순서로 돌려준다. 분석된 채팅이 없으면 빈 배열. */
export function topSentiments(sentiment: Sentiment, limit = 3): SentimentShare[] {
  const distribution = sentiment.distribution
  if (!distribution || sentiment.analyzedChat === 0) return []

  return Object.entries(distribution)
    .filter(([, ratio]) => typeof ratio === 'number' && ratio > 0)
    .sort(([, a], [, b]) => (b as number) - (a as number))
    .slice(0, limit)
    .map(([label, ratio]) => ({
      label: label as SentimentLabel,
      text: sentimentLabelText(label as SentimentLabel),
      ratio: ratio as number,
      barClass: sentimentBarClass(label as SentimentLabel)
    }))
}
