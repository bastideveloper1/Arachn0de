package com.r0ybt.arachn0de.bank

/** Guards also cover batch editors, subtree deletion and existing conversion paths. */
internal object BankInvariants {
    fun install(db:androidx.sqlite.db.SupportSQLiteDatabase) {
        fun guard(name:String,event:String,table:String,condition:String,message:String) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS $name BEFORE $event ON $table WHEN $condition BEGIN SELECT RAISE(ABORT, '$message'); END")
        }
        guard("bank_task_history_delete","DELETE","nodes","EXISTS(SELECT 1 FROM bank_allocations WHERE nodeId=OLD.id) OR EXISTS(SELECT 1 FROM bank_accounts WHERE id=OLD.id)","Banca: conserva la cuenta o tarea con historial financiero.")
        guard("bank_root_history_delete","DELETE","projects","EXISTS(SELECT 1 FROM bank_roots WHERE id=OLD.id)","Banca: conserva el Núcleo financiero y su historial.")
        guard("bank_account_owner_delete","DELETE","persons","EXISTS(SELECT 1 FROM bank_accounts WHERE personId=OLD.id)","Banca: la persona tiene cuentas financieras.")
        val invalidBankNode="EXISTS(SELECT 1 FROM bank_roots WHERE id=NEW.projectId) AND NOT EXISTS(SELECT 1 FROM bank_accounts WHERE id=NEW.id AND rootId=NEW.projectId AND NEW.purpose='LAYER' AND NEW.parentId IS NULL)"
        guard("bank_only_accounts_insert","INSERT","nodes",invalidBankNode,"Banca: crea las cuentas desde el menú de Banca.")
        guard("bank_only_accounts_update","UPDATE OF projectId,parentId,purpose","nodes",invalidBankNode,"Banca: solo admite sus cuentas registradas.")
        guard("bank_no_recurrence_insert","INSERT","recurrence_rules","EXISTS(SELECT 1 FROM bank_roots WHERE id=NEW.projectId)","Banca: los pagos recurrentes deben crearse en un Núcleo normal.")
        guard("bank_no_recurrence_update","UPDATE OF projectId,parentId","recurrence_rules","EXISTS(SELECT 1 FROM bank_roots WHERE id=NEW.projectId)","Banca: conserva los pagos en su Núcleo original.")
        guard("bank_account_flat_insert","INSERT","bank_accounts","NOT EXISTS(SELECT 1 FROM bank_roots WHERE id=NEW.rootId) OR EXISTS(SELECT 1 FROM nodes WHERE id=NEW.id AND (projectId IS NOT NEW.rootId OR parentId IS NOT NULL OR purpose!='LAYER'))","Banca: cada cuenta pertenece directamente a su Núcleo.")
        guard("bank_account_flat_update","UPDATE OF id,rootId","bank_accounts","EXISTS(SELECT 1 FROM nodes WHERE id=NEW.id AND (projectId IS NOT NEW.rootId OR parentId IS NOT NULL OR purpose!='LAYER'))","Banca: no se permiten cuentas anidadas.")
        val paid="(SELECT COALESCE(SUM(a.amount),0) FROM bank_allocations a JOIN bank_operations o ON a.operationId=o.id WHERE a.nodeId=OLD.id AND o.voided=0)"
        val invalidContext="NEW.contextAccountId IS NULL AND (NEW.kind!='DIRECT' OR NEW.sourceAccountId IS NOT NULL OR NEW.destinationAccountId IS NOT NULL)"
        guard("bank_direct_context_insert","INSERT","bank_operations",invalidContext,"Solo DIRECT admite contexto sin cuenta.")
        guard("bank_direct_context_update","UPDATE","bank_operations",invalidContext,"Solo DIRECT admite contexto sin cuenta.")
        guard("bank_task_amount","UPDATE OF amountMinor,currencyCode,purpose","nodes","$paid>0 AND (NEW.amountMinor IS NULL OR NEW.amountMinor<$paid OR NEW.currencyCode IS NOT 'CLP' OR NEW.purpose!='ACTION')","Banca: corrige primero los pagos antes de reducir o retirar el pago.")
        guard("bank_account_structure","UPDATE OF purpose,projectId,parentId","nodes","EXISTS(SELECT 1 FROM bank_accounts a WHERE a.id=OLD.id AND (NEW.purpose!='LAYER' OR NEW.projectId IS NOT a.rootId OR NEW.parentId IS NOT NULL))","Banca: la cuenta debe conservar su Núcleo y estructura.")
        // Explicit task state changes relinquish Banca's automatic-state ownership,
        // even if the wall-clock timestamp is identical. Payment inserts its new marker afterwards.
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS bank_manual_state AFTER UPDATE OF isCompleted,awaitingVerification ON nodes
            WHEN OLD.isCompleted!=NEW.isCompleted OR OLD.awaitingVerification!=NEW.awaitingVerification
            BEGIN UPDATE bank_allocations SET stateAt=NULL WHERE nodeId=NEW.id; END""")
        // Historical ownership markers remain readable; new finance operations never change task states.
        db.execSQL("DROP TRIGGER IF EXISTS bank_increased_debt")
        val outstanding="NEW.amountMinor>"+paid
        guard("bank_complete_pending","UPDATE OF isCompleted","nodes",
            "NEW.isCompleted=1 AND OLD.isCompleted=0 AND NEW.currencyCode='CLP' AND NEW.amountMinor IS NOT NULL AND $outstanding",
            "Pago pendiente: registra el pago antes de marcar Completada.")
        guard("bank_completed_amount","UPDATE OF amountMinor,currencyCode","nodes",
            "NEW.isCompleted=1 AND NEW.currencyCode='CLP' AND NEW.amountMinor IS NOT OLD.amountMinor AND $outstanding",
            "La corrección crearía deuda en una tarjeta Completada. Cambia primero su estado.")
    }
}
