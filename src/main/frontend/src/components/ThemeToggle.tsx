import React from 'react';
import { Moon, Sun } from 'lucide-react';
import { useT } from '../i18n';
import { useTheme } from '../theme';

/** Dark/light switch, shared by the app header and the sign-in pages. */
const ThemeToggle: React.FC = () => {
    const t = useT();
    const { theme, toggleTheme } = useTheme();
    const label = theme === 'dark' ? t('header.toLight') : t('header.toDark');

    return (
        <button
            type="button"
            className="btn btn-ghost btn-icon"
            onClick={toggleTheme}
            title={label}
            aria-label={label}
        >
            {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
        </button>
    );
};

export default ThemeToggle;
