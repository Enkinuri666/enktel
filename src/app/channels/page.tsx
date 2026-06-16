import { Metadata } from "next";
import ChannelGrid from "@/components/channels/ChannelGrid";

export const metadata: Metadata = {
  title: "Channel List",
  description: "Browse our full channel list with 10,000+ live TV channels across sports, movies, news, entertainment, kids, documentary and music.",
};

interface ChannelsPageProps {
  searchParams: { category?: string };
}

export default function ChannelsPage({ searchParams }: ChannelsPageProps) {
  const category = searchParams.category || "All";

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-8">
        <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">
          Channel{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            List
          </span>
        </h1>
        <p className="text-brand-muted text-lg">
          Browse our complete selection of 10,000+ live channels. Filter by category or search by name.
        </p>
      </div>
      <ChannelGrid initialCategory={category} />
    </div>
  );
}
