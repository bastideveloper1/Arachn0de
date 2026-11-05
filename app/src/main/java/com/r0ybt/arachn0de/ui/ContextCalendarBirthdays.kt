package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.r0ybt.arachn0de.calendar.CalendarBirthdays
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.CalendarDay
import com.r0ybt.arachn0de.ui.state.LoadState
import kotlinx.coroutines.flow.combine

@Composable internal fun rememberContextBirthdays(repository:PersonRepository):CalendarBirthdays? {
    var index by remember(repository) {mutableStateOf<CalendarBirthdays?>(null)}
    val load=remember(repository) {LoadState()}
    LaunchedEffect(repository,load.attempt) {
        load.collect(combine(repository.observePeople(),repository.observeBirthdays()) {people,birthdays->
            CalendarBirthdays(people,birthdays)
        }) {index=it}
    }
    LoadErrorDialog(load)
    return index
}

@Composable internal fun ContextBirthdayCards(index:CalendarBirthdays?,day:CalendarDay?) {
    var personId by rememberSaveable {mutableStateOf<String?>(null)}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
    day?.let {selected->index?.on(selected).orEmpty().forEach {birthday->
        key(birthday.person.id) {
            CalendarBirthdayRow(birthday,compact=true) {personId=birthday.person.id}
        }
    }}
    }
    personId?.takeIf {index?.containsPerson(it)==true}?.let {id->
        PersonDetailDialog(id) {personId=null}
    }
}
