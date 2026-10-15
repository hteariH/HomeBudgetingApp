import { useEffect, useState } from 'react';

/** What `/api/instance` says about this server; read before anyone signs in. */
export interface InstanceInfo {
    name: string;
    version: string;
    apiVersion: number;
    setupRequired: boolean;
    registrationOpen: boolean;
    mailEnabled: boolean;
    bankProviders: string[];
}

export async function fetchInstance(): Promise<InstanceInfo> {
    const res = await fetch('/api/instance');
    if (!res.ok) throw new Error(res.statusText);
    return res.json();
}

/**
 * Null until the answer arrives, and if it never does - the sign-in pages then show only
 * what always works (signing in), rather than links that might lead nowhere.
 */
export function useInstance(): InstanceInfo | null {
    const [info, setInfo] = useState<InstanceInfo | null>(null);
    useEffect(() => {
        let live = true;
        fetchInstance()
            .then((i) => live && setInfo(i))
            .catch(() => undefined);
        return () => {
            live = false;
        };
    }, []);
    return info;
}
