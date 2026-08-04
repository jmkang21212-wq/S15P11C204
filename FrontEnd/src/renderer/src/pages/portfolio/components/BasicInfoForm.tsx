import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Input } from '@renderer/components/ui/input'
import { Label } from '@renderer/components/ui/label'
import { Textarea } from '@renderer/components/ui/textarea'

/**
 * 포트폴리오 기본 정보 편집 폼.
 *
 * 제어 컴포넌트로만 두고 저장은 페이지가 한 번에 처리한다.
 * 연락처는 비워 둘 수 있어, 빈 문자열은 페이지에서 null로 바꿔 보낸다.
 */

export interface BasicInfoValues {
  title: string
  introduction: string
  contactEmail: string
  contactUrl: string
}

interface BasicInfoFormProps {
  values: BasicInfoValues
  onChange: (patch: Partial<BasicInfoValues>) => void
}

export default function BasicInfoForm({ values, onChange }: BasicInfoFormProps): React.JSX.Element {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">기본 정보</CardTitle>
        <p className="text-caption text-muted-foreground">공개 포트폴리오에 그대로 노출됩니다.</p>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <div className="flex flex-col gap-2">
          <Label htmlFor="portfolio-title">제목</Label>
          <Input
            id="portfolio-title"
            value={values.title}
            onChange={(e) => onChange({ title: e.target.value })}
            placeholder="예: 한별 TV"
            maxLength={60}
          />
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor="portfolio-introduction">소개</Label>
          <Textarea
            id="portfolio-introduction"
            value={values.introduction}
            onChange={(e) => onChange({ introduction: e.target.value })}
            placeholder="어떤 방송을 하는지, 언제 방송하는지 알려 주세요."
            rows={5}
            maxLength={500}
          />
          <span className="self-end font-mono text-caption text-muted-foreground">
            {values.introduction.length} / 500
          </span>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="flex flex-col gap-2">
            <Label htmlFor="portfolio-email">연락 이메일</Label>
            <Input
              id="portfolio-email"
              type="email"
              value={values.contactEmail}
              onChange={(e) => onChange({ contactEmail: e.target.value })}
              placeholder="business@example.com"
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="portfolio-url">연락 링크</Label>
            <Input
              id="portfolio-url"
              type="url"
              value={values.contactUrl}
              onChange={(e) => onChange({ contactUrl: e.target.value })}
              placeholder="https://example.com/contact"
            />
          </div>
        </div>
        <p className="text-caption text-muted-foreground">
          비워 두면 공개 포트폴리오에 연락처가 표시되지 않습니다.
        </p>
      </CardContent>
    </Card>
  )
}
