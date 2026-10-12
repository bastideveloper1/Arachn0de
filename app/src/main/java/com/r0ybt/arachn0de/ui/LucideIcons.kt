package com.r0ybt.arachn0de.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Official vendored SVG geometry; unknown small actions keep their readable labels. */
@Composable internal fun LucideIcon(imageVector:ImageVector,contentDescription:String?,modifier:Modifier=Modifier,tint:Color=Arachn0deColors.TextPrimary) {
    val resource=if(contentDescription?.startsWith("Vencimiento")==true) R.drawable.lucide_calendar_clock else when(imageVector.name.substringAfterLast('.')) {
        "Search"->R.drawable.lucide_search
        "Star"->R.drawable.lucide_star_selected
        "StarBorder"->R.drawable.lucide_star
        "CalendarToday","CalendarMonth","DateRange","Event"->R.drawable.lucide_calendar_days
        "Notifications","NotificationsActive"->R.drawable.lucide_bell
        "Settings"->R.drawable.lucide_settings
        "Security","Shield","Lock"->R.drawable.lucide_shield
        "Refresh","Repeat"->R.drawable.lucide_repeat
        "Description","Article","StickyNote2"->R.drawable.lucide_sticky_note
        "EventBusy","EventAvailable","EventNote","Schedule"->R.drawable.lucide_calendar_clock
        "AttachFile"->R.drawable.lucide_paperclip
        "MoreVert"->R.drawable.lucide_ellipsis_vertical
        "Menu"->R.drawable.lucide_menu
        "ArrowBack"->R.drawable.lucide_arrow_left
        "FilterList","FilterAlt","Sort"->R.drawable.lucide_list_filter
        "People","Groups","Group"->R.drawable.lucide_users
        "Person"->R.drawable.lucide_user
        "Checklist"->R.drawable.lucide_list_checks
        "HourglassBottom"->R.drawable.lucide_hourglass
        "LocalFireDepartment"->R.drawable.lucide_flag
        "Code","Build"->R.drawable.lucide_code_xml
        "Label","LocalOffer"->R.drawable.lucide_tag
        "Close"->R.drawable.lucide_x
        "ChevronRight"->R.drawable.lucide_chevron_right
        "ExpandLess","ExpandMore","KeyboardArrowDown"->R.drawable.lucide_chevron_down
        "Check"->R.drawable.lucide_check
        "Info","HelpOutline"->R.drawable.lucide_info
        "Backup"->R.drawable.lucide_database
        "Palette"->R.drawable.lucide_palette
        else->null
    }
    if(resource!=null) Icon(painterResource(resource),contentDescription,modifier.then(if(imageVector.name.substringAfterLast('.')=="ExpandLess") Modifier.rotate(180f) else Modifier),tint=tint)
    else if(contentDescription!=null) Text(contentDescription,modifier,color=tint,style=MaterialTheme.typography.labelSmall)
}
