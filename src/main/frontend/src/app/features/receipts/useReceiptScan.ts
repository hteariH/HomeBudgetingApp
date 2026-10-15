import { useCallback, useEffect, useRef, useState } from 'react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { ApiError } from '../../api/http';
import type { ReceiptDto } from '../../api/types';

export const POLL_INTERVAL_MS = 2000;

/** Consecutive polls that may fail to reach the server; the next one in the run fails the scan. Android counts the same. */
export const MAX_FAILED_POLLS = 3;

/** How long a scan waits for the model before giving up, as on Android. */
export const GIVE_UP_AFTER_MS = 6 * 60 * 1000;

export type ScanPhase = 'idle' | 'reading' | 'ready' | 'failed';

export interface ReceiptScan {
    phase: ScanPhase;
    /** The finished reading, once [phase] is `ready`. */
    receipt: ReceiptDto | null;
    /** Why it could not be read: the server's reason, or ours when the read took too long. */
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

/** A 4xx is the server's answer about this receipt (gone, not ours); anything else may pass. */
const isAnswer = (e: unknown) => e instanceof ApiError && e.status >= 400 && e.status < 500;

/**
 * Sends a receipt photo to be read and polls for the answer every two seconds. One scan at a
 * time: starting another, cancelling, or leaving the screen abandons the one in flight. The
 * photo is already on the server once uploaded, so a dropped poll is ridden out; a run of
 * them, or six minutes without an answer, is not.
 */
export function useReceiptScan(): ReceiptScan {
    const t = useT();
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

    const start = useCallback(
        async (file: File): Promise<ReceiptDto | null> => {
            const run = ++current.current;
            const live = () => current.current === run;
            setPhase('reading');
            setReceipt(null);
            setFailureReason(null);
            setError(null);
            try {
                const { uuid } = await endpoints.uploadReceipt(file);
                const deadline = Date.now() + GIVE_UP_AFTER_MS;
                let failedPolls = 0;
                for (;;) {
                    if (!live()) return null;
                    if (Date.now() >= deadline) {
                        setFailureReason(t('receipt_too_slow'));
                        setPhase('failed');
                        return null;
                    }
                    let read: ReceiptDto | null = null;
                    try {
                        read = await endpoints.receipt(uuid);
                        failedPolls = 0;
                    } catch (e) {
                        if (isAnswer(e) || ++failedPolls >= MAX_FAILED_POLLS) throw e;
                    }
                    if (!live()) return null;
                    if (read?.status === 'READY') {
                        setReceipt(read);
                        setPhase('ready');
                        return read;
                    }
                    if (read?.status === 'FAILED') {
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
        },
        [t],
    );

    return { phase, receipt, failureReason, error, start, cancel: reset, reset };
}
