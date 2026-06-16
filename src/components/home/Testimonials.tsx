"use client";
import { motion } from "framer-motion";
import { Star } from "lucide-react";
import { mockTestimonials } from "@/lib/mock-data";

export default function Testimonials() {
  return (
    <section className="py-20 px-4 sm:px-6 lg:px-8 bg-brand-card/30">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-12">
          <motion.h2
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-3xl sm:text-4xl font-bold text-white mb-4"
          >
            What Our{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
              Customers
            </span>{" "}
            Say
          </motion.h2>
          <p className="text-brand-muted">Trusted by Croatian expats and viewers across Europe and beyond.</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {mockTestimonials.map((testimonial, i) => (
            <motion.div
              key={testimonial.id}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.1 }}
              className="bg-brand-card border border-brand-border rounded-xl p-6 hover:border-brand-primary/30 transition-all duration-300"
            >
              {/* Stars */}
              <div className="flex items-center gap-1 mb-4">
                {[1, 2, 3, 4, 5].map((s) => (
                  <Star
                    key={s}
                    className={`w-4 h-4 ${s <= testimonial.rating ? "text-yellow-400 fill-yellow-400" : "text-brand-border"}`}
                  />
                ))}
              </div>
              <p className="text-brand-muted text-sm leading-relaxed mb-5 italic">
                &ldquo;{testimonial.text}&rdquo;
              </p>
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-gradient-to-br from-brand-primary to-brand-secondary flex items-center justify-center text-white font-bold text-sm">
                  {testimonial.avatar}
                </div>
                <div>
                  <div className="text-white font-semibold text-sm">{testimonial.name}</div>
                  <div className="text-brand-muted text-xs">{testimonial.location}</div>
                </div>
                <div className="ml-auto">
                  <span className="text-xs bg-brand-primary/20 text-brand-primary border border-brand-primary/30 px-2 py-0.5 rounded-full">
                    {testimonial.plan}
                  </span>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
