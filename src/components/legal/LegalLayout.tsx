interface LegalLayoutProps {
  title: string;
  updated: string;
  children: React.ReactNode;
}

export default function LegalLayout({ title, updated, children }: LegalLayoutProps) {
  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <h1 className="text-3xl sm:text-4xl font-black text-white mb-2">{title}</h1>
      <p className="text-brand-muted text-sm mb-10">Last updated: {updated}</p>
      <div className="space-y-8 text-brand-muted text-sm leading-relaxed [&_h2]:text-white [&_h2]:font-bold [&_h2]:text-lg [&_h2]:mb-3 [&_p]:mb-3 [&_ul]:list-disc [&_ul]:pl-5 [&_ul]:space-y-1.5 [&_a]:text-brand-primary [&_a]:hover:underline">
        {children}
      </div>
    </div>
  );
}
