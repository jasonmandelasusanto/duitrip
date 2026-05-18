import { useEffect, useState } from 'react';

declare global {
  interface Window {
    kofiwidget2: {
      init: (text: string, color: string, id: string) => void;
      getHTML: () => string;
    };
  }
}

const SCRIPT_ID = 'kofi-widget-js';
const SCRIPT_SRC = 'https://storage.ko-fi.com/cdn/widget/Widget_2.js';

export default function KofiWidget() {
  const [html, setHtml] = useState<string | null>(null);

  useEffect(() => {
    const render = () => {
      window.kofiwidget2.init('Support Duitrip on Ko-fi', '#4DC3EA', 'C0C11WUOS5');
      setHtml(window.kofiwidget2.getHTML());
    };

    if (document.getElementById(SCRIPT_ID)) {
      if (window.kofiwidget2) render();
      return;
    }

    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.src = SCRIPT_SRC;
    script.async = true;
    script.onload = render;
    document.head.appendChild(script);
  }, []);

  if (!html) return null;
  return <div className="flex justify-center" dangerouslySetInnerHTML={{ __html: html }} />;
}
