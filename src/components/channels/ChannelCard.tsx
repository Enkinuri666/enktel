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
      <div className="cyber-panel cyber-panel-hover rounded-xl p-4 group cursor-pointer">
        <div className="flex items-center gap-3 mb-3">
          <div className="icon-glow rounded-full">
            <ChannelLogo name={channel.name} id={channel.id} size="md" />
          </div>
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
