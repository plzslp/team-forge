export default function PageControls({ page, totalPages, onPage }: {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}) {
  if (totalPages < 2) return null;
  return (
    <nav className="pagination" aria-label="페이지 이동">
      <button type="button" className="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>이전</button>
      <span>{page + 1} / {totalPages}</span>
      <button type="button" className="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>다음</button>
    </nav>
  );
}
