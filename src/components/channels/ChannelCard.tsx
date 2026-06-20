import Link from "next/link";
import { Channel } from "@/types";
import Badge from "@/components/ui/Badge";
import ChannelLogo from "@/components/ui/ChannelLogo";

interface ChannelCardProps {
  channel: Channel;
}

export default function ChannelCard({ channel }: ChannelCardProps) {
  return (
    <Link href={`/epg?channel=${channel.id}`}>
      <div className="bg-brand-card border border-brand-border rounded-xl p-4 hover:border-brand-primary/50 hover:shadow-lg hover:shadow-brand-primary/10 transition-all duration-300 group cursor-pointer">
        <div className="flex items-center gap-3 mb-3">
          <ChannelLogo name={channel.name} id={channel.id} size="md" />
          <div className="flex-1 min-w-0">
            <h3 className="text-white font-semibold text-sm truncate group-hover:text-brand-primary transition-colors">
              {channel.name}
            </h3>
            <p className="text-brand-muted text-xs">{channel.country}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <Badge variant="primary" size="sm">{channel.category}</Badge>
          {channel.isHD && <Badge variant="secondary" size="sm">HD</Badge>}
        </div>
      </div>
    </Link>
  );
}
