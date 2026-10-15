import React from 'react';

/**
 * A QR code the server already encoded, as rows of '1' and '0'. Drawn black on white with a
 * quiet zone, whatever the theme - the one thing on a screen whose job is to be scanned.
 */
export const QrRows: React.FC<{ rows: string[] }> = ({ rows }) => {
    if (rows.length === 0) return null;
    const n = rows.length;
    return (
        <div className="self-center rounded-lg bg-white p-3">
            <svg width={200} height={200} viewBox={`0 0 ${n} ${n}`} shapeRendering="crispEdges" role="img" aria-label="QR">
                {rows.flatMap((row, y) =>
                    [...row].map((cell, x) => (cell === '1' ? <rect key={`${x}-${y}`} x={x} y={y} width={1.02} height={1.02} fill="#000" /> : null)),
                )}
            </svg>
        </div>
    );
};
