package com.homebudget.app.ai

/** The wire protocols a receipt-reading model can speak. */
enum class AiProviderType { OPENAI_COMPATIBLE, ANTHROPIC, GEMINI }

/** Whether a stored provider belongs to the whole instance or to one household. */
enum class AiProviderScope { INSTANCE, HOUSEHOLD }

/** Where the provider in use came from, in the order they are consulted. */
enum class AiProviderSource { HOUSEHOLD, ENVIRONMENT, INSTANCE, NONE }
