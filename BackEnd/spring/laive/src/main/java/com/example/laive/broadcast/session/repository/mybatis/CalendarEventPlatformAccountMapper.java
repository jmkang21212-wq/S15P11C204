package com.example.laive.broadcast.session.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

/** Validates the many-to-many account assignment of a scheduled broadcast. */
@Mapper
public interface CalendarEventPlatformAccountMapper {

    @Select("""
            <script>
            SELECT count(*)
              FROM calendar_event_platform_accounts
             WHERE calendar_event_id = #{calendarEventId}
               AND platform_account_id IN
               <foreach collection="platformAccountIds" item="id" open="(" separator="," close=")">
                   #{id}
               </foreach>
            </script>
            """)
    long countLinkedAccounts(
            @Param("calendarEventId") long calendarEventId,
            @Param("platformAccountIds") Set<Long> platformAccountIds
    );
}
