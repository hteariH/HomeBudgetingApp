import React from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp } from 'lucide-react';
import { AppRoutes } from '../lib/routes';

/** The wordmark: a gradient tile with the product's mark, then the name, second half in the accent. */
export const BrandMark: React.FC<{ showWordmark: boolean; onClick?: () => void }> = ({ showWordmark, onClick }) => (
    <Link to={AppRoutes.dashboard} onClick={onClick} className="flex shrink-0 items-center gap-2.5 rounded-md">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-[#6366F1] to-[#06B6D4] text-white">
            <TrendingUp size={18} />
        </span>
        {showWordmark && (
            <span className="text-[15px] font-bold tracking-tight">
                <span className="text-ink">Personal</span>
                <span className="text-brand-light">Money</span>
            </span>
        )}
    </Link>
);
