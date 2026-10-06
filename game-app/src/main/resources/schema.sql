-- One table. The json column holds a serialised StoredEvent: the same record the kernel uses, nothing else.
create table if not exists campaign_event (
    campaign_id varchar(100) not null,
    seq         bigint       not null,
    at          timestamp    not null,
    json        text         not null,
    primary key (campaign_id, seq)
);
