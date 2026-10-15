import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Copy, Trash2, UserPlus } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useT } from '../../../i18n';
import { endpoints } from '../../api/endpoints';
import { useHousehold, useMe } from '../../api/hooks';
import { qk } from '../../api/queryClient';
import type { CreatedInviteDto, MemberDto } from '../../api/types';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorBanner, errorText } from '../../components/ErrorBanner';
import { Card } from '../../components/forms';
import { QrRows } from '../../components/QrRows';
import { AppRoutes } from '../../lib/routes';
import { prefs } from '../../prefs';
import { Page, PageLoading } from '../../shell/Page';

type Pending = { kind: 'remove' | 'owner'; member: MemberDto } | { kind: 'leave' };

const memberName = (m: MemberDto) => `${m.firstName} ${m.lastName}`.trim() || m.email;

/**
 * The people who share these books: who is in, who owns the household, and - for the owner -
 * the invitations that bring somebody else in. A member can only leave.
 */
const HouseholdPage: React.FC = () => {
    const t = useT();
    const navigate = useNavigate();
    const client = useQueryClient();
    const me = useMe();
    const household = useHousehold();
    const isOwner = me.data?.householdRole === 'OWNER';
    const invites = useQuery({ queryKey: qk.invites, queryFn: endpoints.invites, enabled: isOwner });

    const [nameDraft, setNameDraft] = useState<string | null>(null);
    const [created, setCreated] = useState<{ url: string; invite: CreatedInviteDto } | null>(null);
    const [pending, setPending] = useState<Pending | null>(null);

    const refreshHousehold = () => Promise.all([client.invalidateQueries({ queryKey: qk.household }), client.invalidateQueries({ queryKey: qk.me })]);

    const rename = useMutation({
        mutationFn: endpoints.renameHousehold,
        onSuccess: (data) => {
            setNameDraft(null);
            client.setQueryData(qk.household, data);
            client.invalidateQueries({ queryKey: qk.me });
        },
    });
    const invite = useMutation({
        mutationFn: () => endpoints.createInvite(window.location.origin),
        onSuccess: (data) => {
            setCreated({ url: window.location.origin + data.path, invite: data });
            client.invalidateQueries({ queryKey: qk.invites });
        },
    });
    const revoke = useMutation({
        mutationFn: endpoints.revokeInvite,
        onSuccess: () => client.invalidateQueries({ queryKey: qk.invites }),
    });
    const remove = useMutation({
        mutationFn: (m: MemberDto) => endpoints.removeMember(m.userId),
        onSuccess: () => {
            setPending(null);
            return refreshHousehold();
        },
    });
    const makeOwner = useMutation({
        mutationFn: (m: MemberDto) => endpoints.transferOwnership(m.userId),
        onSuccess: () => {
            setPending(null);
            return refreshHousehold();
        },
    });
    const leave = useMutation({
        mutationFn: endpoints.leaveHousehold,
        onSuccess: () => {
            // Everything cached and remembered here belongs to the household just left.
            prefs.clearAccountScoped();
            client.clear();
            navigate(AppRoutes.dashboard, { replace: true });
        },
    });

    const choose = (next: Pending) => {
        remove.reset();
        makeOwner.reset();
        leave.reset();
        setPending(next);
    };
    const busy = remove.isPending || makeOwner.isPending || leave.isPending;
    const dialogError = remove.error ?? makeOwner.error ?? leave.error;

    if (household.isPending) return <Page title={t('household_title')}><PageLoading /></Page>;

    const data = household.data;
    const members = data?.members ?? [];
    const others = members.length > 1;
    const openInvites = invites.data ?? [];
    const name = nameDraft ?? data?.name ?? '';

    let confirm: { title: string; message: string; label: string; destructive: boolean; run: () => void } | null = null;
    if (pending?.kind === 'remove') {
        const m = pending.member;
        confirm = { title: t('household_remove'), message: t('household_remove_confirm', { name: memberName(m) }), label: t('household_remove'), destructive: true, run: () => remove.mutate(m) };
    } else if (pending?.kind === 'owner') {
        const m = pending.member;
        confirm = { title: t('household_make_owner'), message: t('household_make_owner_confirm', { name: memberName(m) }), label: t('household_make_owner'), destructive: false, run: () => makeOwner.mutate(m) };
    } else if (pending?.kind === 'leave') {
        confirm = { title: t('household_leave'), message: t('household_leave_confirm'), label: t('household_leave'), destructive: true, run: () => leave.mutate() };
    }

    return (
        <Page title={t('household_title')}>
            <div className="flex flex-col gap-4">
                <ErrorBanner error={household.error ?? me.error} fallbackKey="common_load_failed" />

                {isOwner && data && (
                    <Card title={t('common_name')}>
                        <form
                            className="flex items-center gap-2 px-5 pb-4"
                            onSubmit={(e) => {
                                e.preventDefault();
                                if (name.trim() && name.trim() !== data.name) rename.mutate(name.trim());
                            }}
                        >
                            <input
                                className="form-control flex-1"
                                aria-label={t('common_name')}
                                value={name}
                                maxLength={100}
                                onChange={(e) => setNameDraft(e.target.value)}
                            />
                            <button type="submit" className="btn btn-secondary" disabled={rename.isPending || !name.trim() || name.trim() === data.name}>
                                {t('household_rename')}
                            </button>
                        </form>
                        <ErrorBanner error={rename.error} className="mx-5 mb-4" />
                    </Card>
                )}

                <Card title={t('household_members')}>
                    <ul className="divide-y divide-line">
                        {members.map((m) => (
                            <li key={m.userId} className="flex flex-wrap items-center gap-3 px-5 py-3">
                                <div className="min-w-0 flex-1">
                                    <div className="flex items-center gap-2">
                                        <span className="truncate text-sm font-medium text-ink">{memberName(m)}</span>
                                        <span className="shrink-0 rounded-full bg-brand/15 px-2 py-0.5 text-xs font-medium text-brand-light">
                                            {t(m.role === 'OWNER' ? 'household_owner' : 'household_member')}
                                        </span>
                                    </div>
                                    <div className="truncate text-xs text-ink-soft">
                                        <span>{m.email}</span> · {t('household_joined', { date: new Date(m.joinedAt).toLocaleDateString() })}
                                    </div>
                                </div>
                                {isOwner && m.role !== 'OWNER' && (
                                    <>
                                        <button type="button" className="btn btn-ghost" onClick={() => choose({ kind: 'owner', member: m })}>
                                            {t('household_make_owner')}
                                        </button>
                                        <button type="button" className="btn btn-ghost text-bad" onClick={() => choose({ kind: 'remove', member: m })}>
                                            {t('household_remove')}
                                        </button>
                                    </>
                                )}
                            </li>
                        ))}
                    </ul>
                </Card>

                {isOwner && (
                    <Card
                        title={t('household_invite')}
                        action={
                            <button type="button" className="btn btn-primary" disabled={invite.isPending} onClick={() => invite.mutate()}>
                                <UserPlus size={16} /> {t('household_invite')}
                            </button>
                        }
                    >
                        <div className="flex flex-col gap-3 px-5 pb-4">
                            <ErrorBanner error={invite.error ?? invites.error ?? revoke.error} />
                            {created && (
                                <>
                                    <p className="text-sm text-ink-soft">{t('household_invite_ready')}</p>
                                    <div className="flex items-center gap-2">
                                        <input readOnly value={created.url} className="form-control flex-1" onFocus={(e) => e.currentTarget.select()} />
                                        <button type="button" className="btn btn-secondary" onClick={() => navigator.clipboard?.writeText(created.url)}>
                                            <Copy size={16} /> {t('admin_copy')}
                                        </button>
                                    </div>
                                    <QrRows rows={created.invite.qrRows ?? []} />
                                </>
                            )}
                        </div>
                    </Card>
                )}

                {isOwner && openInvites.length > 0 && (
                    <Card title={t('household_invites_open')}>
                        <ul className="divide-y divide-line">
                            {openInvites.map((i) => (
                                <li key={i.id} className="flex items-center gap-3 px-5 py-3">
                                    <div className="min-w-0 flex-1 text-sm text-ink">
                                        {i.createdBy && <span className="font-medium">{i.createdBy} · </span>}
                                        <span className="text-ink-soft">{t('household_expires', { date: new Date(i.expiresAt).toLocaleDateString() })}</span>
                                    </div>
                                    <button
                                        type="button"
                                        className="btn btn-ghost text-bad"
                                        aria-label={t('household_revoke')}
                                        title={t('household_revoke')}
                                        disabled={revoke.isPending}
                                        onClick={() => revoke.mutate(i.id)}
                                    >
                                        <Trash2 size={16} />
                                    </button>
                                </li>
                            ))}
                        </ul>
                    </Card>
                )}

                {me.data && !isOwner && (
                    <div>
                        <button type="button" className="btn btn-ghost text-bad" onClick={() => choose({ kind: 'leave' })}>
                            {t('household_leave')}
                        </button>
                    </div>
                )}
                {isOwner && others && <p className="text-sm text-ink-soft">{t('household_leave_owner_hint')}</p>}
            </div>

            <ConfirmDialog
                open={confirm !== null}
                title={confirm?.title ?? ''}
                destructive={confirm?.destructive}
                busy={busy}
                confirmLabel={confirm?.label}
                message={
                    <>
                        <p>{confirm?.message}</p>
                        {dialogError != null && <p className="mt-3 text-bad">{errorText(dialogError, t)}</p>}
                    </>
                }
                onConfirm={() => confirm?.run()}
                onCancel={() => !busy && setPending(null)}
            />
        </Page>
    );
};

export default HouseholdPage;
