% Rules for merging patches:
% It needs to have at least two +2 votes
% And the author of the patch can not vote

% These are some sample Facts like the ones Gerrit will provide for each patch
% commit_author(user(1000000), 'Castulo J. Martinez', 'castulo.martinez@intel.com').
% commit_committer(user(1000000), 'Castulo J. Martinez', 'castulo.martinez@intel.com').
% commit_message('Add plugin support to Gerrit').

submit_rule(submit(CR)) :-
    gerrit:max_with_block(-2, 2, 'Code-Review', CR),
    gerrit:commit_author(Author, _, _),
    CR = label(_, ok(Reviewer)),
    Author \= Reviewer,
    !.

submit_rule(submit(CR, N)) :-
    gerrit:max_with_block(-2, 2, 'Code-Review', CR),
    N = label('Non-Author-Code-Review', need(_)).
