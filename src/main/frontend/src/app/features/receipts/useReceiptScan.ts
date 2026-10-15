import { useCallback, useEffect, useRef, useState } from 'react';
import { endpoints } from '../../api/endpoints';
import type { ReceiptDto } from '../../api/types';

export const POLL_INTERVAL_MS = 2000;

export type ScanPhase = 'idle' | 'reading' | 'ready' | 'failed';

export interface ReceiptScan {
    phase: ScanPhase;
    /** The finished reading, once [phase] is `ready`. */
    receipt: ReceiptDto | null;
    /** Why the model could not read it, when the server marked the receipt FAILED. */
    failureReason: string | null;
    /** A request that never got an answer worth reading: a refused upload, a dropped connection. */
    error: unknown;
    /** Uploads the photo and polls until it is read. Resolves with the draft, or null when it failed or was cancelled. */
    start: (file: File) => Promise<ReceiptDto | null>;
    /** Stops waiting. The server keeps the receipt a day, then drops it unlinked. */
    cancel: () => void;
    reset: () => void;
}

const wait = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms));

/**
 * Sends a receipt photo to be read and polls for the answer every two seconds. One scan at a
 * time: starting another, cancelling, or leaving the screen abandons the one in flight.
 */
export function useReceiptScan(): ReceiptScan {
    const [phase, setPhase] = useState<ScanPhase>('idle');
    const [receipt, setReceipt] = useState<ReceiptDto | null>(null);
    const [failureReason, setFailureReason] = useState<string | null>(null);
    const [error, setError] = useState<unknown>(null);
    // Each scan gets a number; a result that finds a newer number in the ref was abandoned.
    const current = useRef(0);

    useEffect(
        () => () => {
            current.current++;
        },
        [],
    );

    const reset = useCallback(() => {
        current.current++;
        setPhase('idle');
        setReceipt(null);
        setFailureReason(null);
        setError(null);
    }, []);

    const start = useCallback(async (file: File): Promise<ReceiptDto | null> => {
        const run = ++current.current;
        const live = () => current.current === run;
        setPhase('reading');
        setReceipt(null);
        setFailureReason(null);
        setError(null);
        try {
            const { uuid } = await endpoints.uploadReceipt(file);
            for (;;) {
                if (!live()) return null;
                const read = await endpoints.receipt(uuid);
                if (!live()) return null;
                if (read.status === 'READY') {
                    setReceipt(read);
                    setPhase('ready');
                    return read;
                }
                if (read.status === 'FAILED') {
                    setFailureReason(read.failureReason ?? null);
                    setPhase('failed');
                    return null;
                }
                await wait(POLL_INTERVAL_MS);
            }
        } catch (e) {
            if (live()) {
                setError(e);
                setPhase('failed');
            }
            return null;
        }
    }, []);

    return { phase, receipt, failureReason, error, start, cancel: reset, reset };
}
