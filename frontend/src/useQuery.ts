import { useEffect, useState } from 'react';
import { api } from './api';

type QueryResult<T> = { data: T | null; error: unknown; loading: boolean };

/** 이전 검색이나 페이지 요청이 새 결과를 덮어쓰지 않도록 취소한다. */
export function useQuery<T>(path: string | null, revision = 0): QueryResult<T> {
  const key = path === null ? null : `${revision}:${path}`;
  const [result, setResult] = useState<{ key: string; data: T | null; error: unknown } | null>(null);

  useEffect(() => {
    if (!path || key === null) return;
    const controller = new AbortController();
    api.get<T>(path, controller.signal).then(
      data => { if (!controller.signal.aborted) setResult({ key, data, error: null }); },
      error => { if (!controller.signal.aborted) setResult({ key, data: null, error }); },
    );
    return () => controller.abort();
  }, [path, key]);

  if (key === null) return { data: null, error: null, loading: false };
  if (result?.key !== key) return { data: null, error: null, loading: true };
  return { data: result.data, error: result.error, loading: false };
}
