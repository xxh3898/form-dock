---
title: CSV Export Requirements
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Goal

Creator가 외부 분석을 위해 Survey Response를 CSV로 내보낼 수 있다.

# 2. Encoding

UTF-8.

한글 Excel 호환을 위해 BOM 여부는 구현 단계에서 테스트 후 확정한다.

# 3. Columns

기본 후보:

```text
response_id
submitted_at
Q1
Q2
...
```

Question title 변경은 첫 Response 이후 lock되므로 export header 의미가 유지된다.

# 4. MULTIPLE_CHOICE

직렬화 방식 `TBD`.

후보:

- delimiter-separated single column
- option별 boolean columns

dogfooding 후 결정 가능.

# 5. Security

Creator owner authorization 필수.

CSV formula injection 위험을 고려한다.

Text value가 `=`, `+`, `-`, `@`로 시작할 때 안전한 export 정책을 적용한다.
