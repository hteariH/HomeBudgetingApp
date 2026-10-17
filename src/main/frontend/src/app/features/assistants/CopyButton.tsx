import React, { useState } from 'react';
import { Check, Copy } from 'lucide-react';
import { useT } from '../../../i18n';

/** Copies `text` to the clipboard and says so for a moment. */
export const CopyButton: React.FC<{ text: string; label?: string }> = ({ text, label }) => {
    const t = useT();
    const [copied, setCopied] = useState(false);
    const copy = async () => {
        try {
            await navigator.clipboard?.writeText(text);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch {
            /* The text is on screen to select by hand. */
        }
    };
    return (
        <button type="button" className="btn btn-secondary shrink-0" onClick={copy} aria-label={label}>
            {copied ? <Check size={16} /> : <Copy size={16} />} {t(copied ? 'assistants_copied' : 'assistants_copy')}
        </button>
    );
};
