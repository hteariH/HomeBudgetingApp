import {
    ArrowRightLeft,
    Book,
    CirclePlus,
    Laptop,
    Map as MapIcon,
    ShoppingCart,
    Tag,
    Truck,
    Banknote,
    Briefcase,
    Car,
    Coffee,
    CreditCard,
    DollarSign,
    Dumbbell,
    Ellipsis,
    Film,
    Flower2,
    Gift,
    GraduationCap,
    Heart,
    Home,
    Landmark,
    PawPrint,
    PiggyBank,
    Plane,
    Repeat,
    ShoppingBag,
    Sparkles,
    TrendingUp,
    Undo2,
    UtensilsCrossed,
    Wallet,
    Zap,
    type LucideIcon,
} from 'lucide-react';

/**
 * The icon keys stored on accounts, categories and subscriptions are Material names -
 * the Compose client's IconLibrary.kt. Each maps to the nearest lucide glyph, so a row
 * created on the phone gets a recognisable icon here.
 */
const ICONS: Record<string, LucideIcon> = {
    wallet: Wallet,
    bank: Landmark,
    card: CreditCard,
    cash: DollarSign,
    savings: PiggyBank,
    currency_exchange: ArrowRightLeft,
    restaurant: UtensilsCrossed,
    directions_car: Car,
    home: Home,
    bolt: Zap,
    movie: Film,
    favorite: Heart,
    shopping_bag: ShoppingBag,
    local_cafe: Coffee,
    school: GraduationCap,
    flight: Plane,
    subscriptions: Repeat,
    redeem: Gift,
    pets: PawPrint,
    fitness_center: Dumbbell,
    spa: Flower2,
    account_balance: Landmark,
    more_horiz: Ellipsis,
    payments: Banknote,
    work: Briefcase,
    stars: Sparkles,
    trending_up: TrendingUp,
    undo: Undo2,

    // The server's default categories, and anything saved by the React client that came
    // before the Compose one, carry lucide names instead. The Compose client drew all of
    // them as "…"; they may as well be the icons they name.
    tag: Tag,
    briefcase: Briefcase,
    laptop: Laptop,
    'trending-up': TrendingUp,
    'shopping-cart': ShoppingCart,
    truck: Truck,
    car: Car,
    heart: Heart,
    film: Film,
    'shopping-bag': ShoppingBag,
    zap: Zap,
    book: Book,
    coffee: Coffee,
    map: MapIcon,
    'more-horizontal': Ellipsis,
    'plus-circle': CirclePlus,
    'dollar-sign': DollarSign,
};

/** The keys offered when picking an icon, in IconLibrary.pickerSet's order. */
export const PICKER_ICONS = [
    'wallet', 'bank', 'card', 'cash', 'savings', 'currency_exchange',
    'restaurant', 'directions_car', 'home', 'bolt', 'movie', 'favorite',
    'shopping_bag', 'local_cafe', 'school', 'flight', 'subscriptions', 'redeem',
    'pets', 'fitness_center', 'spa', 'account_balance', 'payments', 'work', 'stars',
    'trending_up', 'undo',
];

export const iconFor = (key: string | null | undefined): LucideIcon => (key && ICONS[key]) || Ellipsis;

export const KNOWN_ICON_KEYS = Object.keys(ICONS);
