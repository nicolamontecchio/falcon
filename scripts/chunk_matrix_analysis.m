function chunk_matrix_analysis(mfilepath)

    M = dlmread(mfilepath);

    relpos = find(M(:,2)==1);

    L = 4;

    MF = M(:,3:size(M,2));
    MF = filter(ones(1,L),L,MF')';

    MFmax = sum(MF,2);
    [aa ii] = sort(MFmax,'descend');
    %    disp(ii(1:20)')
    
    for i=1:100
        fprintf('precision @%3d: %10.5f    recall: %10.5f\n', i, precision(ii(1:i),relpos),recall(ii(1:i),relpos));
    end

    % per ogni chunk, calcola in che ordine ? arrivato ogni file
    [xxx MR_invmap] = sort(M(:,3:size(M,2)),'descend');     
    MR = zeros(size(MR_invmap));
    for i = 1:size(MR,1)
        for j = 1:size(MR,2)
            s = MR_invmap(i,j);
            MR(s,j) = i;
        end
    end

    % strategia del vecchio codice pitone
    MR_invsq = 1 ./ (max(3,MR).^2);
    MR_invsqF = filter(ones(1,L),L,MR_invsq')';
    MR_invsqF_max = max(MR_invsqF')';

    [qq ii] = sort(MR_invsqF_max,'descend');
    
    for i=1:100
        fprintf('precision @%3d: %10.5f    recall: %10.5f\n', i, precision(ii(1:i),relpos),recall(ii(1:i),relpos));
    end

end


function p = precision(ranklist,relevant)
    p = 0;
    n = length(ranklist);
    for i = 1:n
        p = p + length(find(relevant == ranklist(i)));
    end
    p = p / n;    
end

function r = recall(ranklist,relevant)
    r = 0;
    n = length(relevant);
    for i = 1:n
        r = r + length(find(ranklist == relevant(i)));
    end
    r = r / n;    
end


