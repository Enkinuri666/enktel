import CategoryGrid, { type PosterItem } from '@/components/CategoryGrid';
import { useCategories, useSeriesList } from '@/lib/queries';
import type { Series } from '@/lib/xtream';

type SeriesItem = PosterItem & { series: Series };

/**
 * Series view: same shell as MoviesPage. Selecting a series would ideally
 * open a season/episode picker sourced from `xtream_series_info` — that
 * endpoint isn't in the Rust backend yet, so for now the click is a no-op
 * placeholder. Adding the picker is the next PR after this one.
 */
export default function SeriesPage() {
  const cats = useCategories('series');
  const series = useSeriesList();

  const items: SeriesItem[] = (series.data ?? []).map((s) => ({
    key: String(s.series_id),
    name: s.name,
    poster: s.cover,
    categoryId: s.category_id,
    rating: s.rating,
    year: s.year,
    series: s,
  }));

  return (
    <CategoryGrid<SeriesItem>
      title="Series"
      subtitle={series.isLoading
        ? 'Loading library…'
        : `${items.length} shows · click a poster (season picker coming next)`}
      categories={cats.data ?? []}
      items={items}
      loading={series.isLoading}
      onOpen={() => {
        // TODO: navigate to a SeriesDetailsPage that fetches
        // `xtream_series_info` for the picked series_id, renders the
        // season/episode tree, and hands each episode URL off to Player.
      }}
    />
  );
}
