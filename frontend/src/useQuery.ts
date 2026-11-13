import { useEffect, useMemo, useState } from 'react';
import { api } from './api';

type QueryResult<T> = { data: T | null; error: unknown; loading: boolean };

/** 이전 검색이나 페이지 요청이 새 결과를 덮어쓰지 않도록 취소한다. */
export function useQuery<T>(path: string | null, revision = 0): QueryResult<T> {
  // 같은 경로로 돌아와도 이전 조회와 새 조회는 별개다.
  const query = useMemo(() => ({ path, revision }), [path, revision]);
  const [result, setResult] = useState<{ query: typeof query; data: T | null; error: unknown } | null>(null);

  useEffect(() => {
    if (!query.path) return;
    const controller = new AbortController();
    api.get<T>(query.path, controller.signal).then(
      data => { if (!controller.signal.aborted) setResult({ query, data, error: null }); },
      error => { if (!controller.signal.aborted) setResult({ query, data: null, error }); },
    );
    return () => controller.abort();
  }, [query]);

  if (path === null) return { data: null, error: null, loading: false };
  if (result?.query !== query) return { data: null, error: null, loading: true };
  return { data: result.data, error: result.error, loading: false };
}
