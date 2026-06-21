import type { Metadata } from "next";
import Card from "@/components/ui/Card";
import { DEVICE_GUIDES } from "@/lib/deviceGuides";

export const metadata: Metadata = { title: "Setup Guides" };

export default function SetupGuidesPage() {
  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="text-center mb-10">
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-3">Setup Guides</h1>
        <p className="text-brand-muted max-w-xl mx-auto">
          Find your device below for step-by-step setup instructions. Your credentials and URLs are always
          available in your{" "}
          <a href="/dashboard" className="text-brand-primary hover:underline">dashboard</a>.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {DEVICE_GUIDES.map((guide) => {
          const Icon = guide.icon;
          return (
            <Card key={guide.id} className="p-6">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 bg-brand-primary/10 border border-brand-primary/30 rounded-lg flex items-center justify-center shrink-0">
                  <Icon className="w-5 h-5 text-brand-primary" />
                </div>
                <div>
                  <h2 className="text-white font-bold">{guide.label}</h2>
                  <p className="text-brand-muted text-xs">{guide.app}</p>
                </div>
              </div>
              <ol className="space-y-2">
                {guide.steps.map((step) => (
                  <li key={step.step} className="flex gap-3 text-sm text-brand-muted">
                    <span className="shrink-0 w-5 h-5 rounded-full bg-white/5 border border-brand-border text-white text-xs flex items-center justify-center font-semibold">
                      {step.step}
                    </span>
                    {step.description}
                  </li>
                ))}
              </ol>
            </Card>
          );
        })}
      </div>

      <div className="mt-10 text-center">
        <p className="text-brand-muted text-sm">
          Still stuck? <a href="/contact" className="text-brand-primary hover:underline">Contact support</a> and we'll help you get streaming.
        </p>
      </div>
    </div>
  );
}
