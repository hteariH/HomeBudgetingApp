import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Copy, KeyRound, Trash2 } from 'lucide-react';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { queryClient, qk } from '../../api/queryClient';
import type { AdminUserDto } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorBanner, errorText } from '../../components/ErrorBanner';
import { Card, Switch } from '../../components/forms';
import { Page } from '../../shell/Page';
import { ProviderForm } from '../ai/ProviderForm';

/**
 * What the person who runs this instance needs: who has an account, whether anyone else
 * may create one, and a way back in for somebody who lost their password on an instance
 * that cannot send mail.
 */
const AdminPage: React.FC = () => {
    const t = useT();
    const users = useQuery({ queryKey: qk.adminUsers, queryFn: endpoints.adminUsers });
    const registration = useQuery({ queryKey: qk.adminRegistration, queryFn: endpoints.adminRegistration });
    const mcp = useQuery({ queryKey: qk.adminMcp, queryFn: endpoints.adminMcp });
    const provider = useQuery({ queryKey: qk.adminAiProvider, queryFn: endpoints.adminAiProvider });
    const [link, setLink] = useState<{ email: string; url: string } | null>(null);
    const [deleting, setDeleting] = useState<AdminUserDto | null>(null);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const toggle = useMutation({
        mutationFn: endpoints.setAdminRegistration,
        onSuccess: (data) => queryClient.setQueryData(qk.adminRegistration, data),
    });
    const toggleMcp = useMutation({
        mutationFn: endpoints.setAdminMcp,
        onSuccess: (data) => {
            queryClient.setQueryData(qk.adminMcp, data);
            queryClient.invalidateQueries({ queryKey: qk.instance });
        },
    });
    const reset = useMutation({
        mutationFn: (u: AdminUserDto) =>
            endpoints.adminResetLink(u.id).then((r) => ({ email: u.email, url: window.location.origin + r.path })),
        onSuccess: setLink,
    });
    const remove = useMutation({
        mutationFn: (u: AdminUserDto) => endpoints.adminDeleteUser(u.id),
        onSuccess: () => {
            setDeleting(null);
            queryClient.invalidateQueries({ queryKey: qk.adminUsers });
        },
        onError: (e) => setDeleteError(errorText(e, t)),
    });

    return (
        <Page title={t('admin_title')}>
            <div className="flex flex-col gap-4">
                <Card title={t('admin_registration')}>
                    <div className="px-5 pb-4">
                        <Switch
                            checked={registration.data?.open ?? false}
                            onChange={(open) => toggle.mutate(open)}
                            disabled={registration.isPending || registration.isError || registration.data?.lockedByEnvironment}
                            label={t('admin_registration_open')}
                            description={t(registration.data?.lockedByEnvironment ? 'admin_registration_locked' : 'admin_registration_hint')}
                        />
                        <ErrorBanner error={registration.error ?? toggle.error} />
                    </div>
                </Card>

                <Card title={t('admin_mcp')}>
                    <div className="px-5 pb-4">
                        <Switch
                            checked={mcp.data?.enabled ?? false}
                            onChange={(enabled) => toggleMcp.mutate(enabled)}
                            disabled={mcp.isPending || mcp.isError || mcp.data?.lockedByEnvironment || toggleMcp.isPending}
                            label={t('admin_mcp_enabled')}
                            description={t(mcp.data?.lockedByEnvironment ? 'admin_mcp_locked' : 'admin_mcp_hint')}
                        />
                        <ErrorBanner error={mcp.error ?? toggleMcp.error} />
                    </div>
                </Card>

                <Card title={t('ai_title')}>
                    <div className="px-5 pb-4">
                        <ErrorBanner error={provider.error} fallbackKey="common_load_failed" />
                        {provider.data?.source === 'ENVIRONMENT' ? (
                            <>
                                <p className="text-sm font-medium text-ink">{t('ai_environment_set')}</p>
                                <p className="text-xs text-ink-soft">{t('ai_environment_hint')}</p>
                                {provider.data.host && <p className="mt-2 text-xs text-ink-soft">{t('ai_sent_to', { host: provider.data.host })}</p>}
                            </>
                        ) : (
                            <p className="text-xs text-ink-soft">{t('ai_admin_hint')}</p>
                        )}
                    </div>
                    {provider.data && provider.data.source !== 'ENVIRONMENT' && <ProviderForm mode="admin" view={provider.data} />}
                </Card>

                {link && (
                    <Card title={t('admin_reset_link_ready', { email: link.email })}>
                        <div className="flex items-center gap-2 px-5 pb-4">
                            <input readOnly value={link.url} className="form-control flex-1" onFocus={(e) => e.currentTarget.select()} />
                            <button type="button" className="btn btn-secondary" onClick={() => navigator.clipboard?.writeText(link.url)}>
                                <Copy size={16} /> {t('admin_copy')}
                            </button>
                        </div>
                    </Card>
                )}

                <Card title={t('admin_users')}>
                    <ErrorBanner error={users.error ?? reset.error} />
                    <ul className="divide-y divide-line">
                        {(users.data ?? []).map((u) => (
                            <li key={u.id} className="flex items-center gap-3 px-5 py-3">
                                <div className="min-w-0 flex-1">
                                    <div className="truncate text-sm font-medium text-ink">{u.email}</div>
                                    <div className="text-xs text-ink-soft">
                                        {u.firstName} {u.lastName}
                                        {u.role === 'ADMIN' && <> · {t('admin_role_admin')}</>}
                                        {' · '}
                                        {t('admin_last_sign_in')}{' '}
                                        {u.lastSignInAt ? new Date(u.lastSignInAt).toLocaleString() : t('admin_never')}
                                    </div>
                                </div>
                                <button type="button" className="btn btn-ghost" aria-label={t('admin_reset_link')} title={t('admin_reset_link')} onClick={() => reset.mutate(u)}>
                                    <KeyRound size={16} />
                                </button>
                                {u.role !== 'ADMIN' && (
                                    <button type="button" className="btn btn-ghost text-bad" aria-label={t('admin_delete_user')} title={t('admin_delete_user')} onClick={() => { setDeleteError(null); setDeleting(u); }}>
                                        <Trash2 size={16} />
                                    </button>
                                )}
                            </li>
                        ))}
                    </ul>
                </Card>
            </div>

            <ConfirmDialog
                open={deleting !== null}
                title={t('admin_delete_user')}
                destructive
                busy={remove.isPending}
                confirmLabel={t('admin_delete_user')}
                message={
                    <>
                        <p>{t('admin_delete_confirm', { email: deleting?.email ?? '' })}</p>
                        {deleteError && <p className="mt-3 text-bad">{deleteError}</p>}
                    </>
                }
                onConfirm={() => deleting && remove.mutate(deleting)}
                onCancel={() => !remove.isPending && setDeleting(null)}
            />
        </Page>
    );
};

export default AdminPage;
